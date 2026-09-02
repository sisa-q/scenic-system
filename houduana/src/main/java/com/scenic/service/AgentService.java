package com.scenic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scenic.entity.KnowledgeDoc;
import com.scenic.entity.ScenicSpot;
import com.scenic.entity.TicketPolicy;
import com.scenic.entity.TimeSlot;
import com.scenic.repository.KnowledgeDocRepository;
import com.scenic.repository.ScenicSpotRepository;
import com.scenic.repository.TicketPolicyRepository;
import com.scenic.repository.TimeSlotRepository;
import com.scenic.vo.WeatherPoint;
import com.scenic.vo.WeatherVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** 游客问答 Agent：Ollama(qwen2.5:3b) + 工具调用(Function Calling) + 知识库 RAG + 危险操作确认 */
@Service
public class AgentService {

    private static final String SYSTEM_PROMPT = "你是智慧景区游客助手，服务于北京故宫智慧景区系统。可以解答故宫景点、开放时间、门票、分时预约、天气、退款等问题，也可以调用工具帮游客查询实时数据或执行操作。";

    private static final String TOOL_TIP = "你可以调用以下工具获取实时信息或执行操作。需要登录的操作（查我的订单/下单/支付/退款）如果游客未登录，请提示先登录。下单流程：可直接从【系统内景点与门票】和【可预约时段】中取票种 id 与时段 id 调用 place_order；不确定时再调用 get_policies 与 get_slots。游客说买 N 张故宫门票但没说票种时，默认选择故宫成人票；没说时段时默认选择【可预约时段】中最近的第一个时段。支付/退款流程：优先使用对话历史中最近出现的订单号（orderNo，32位）或订单ID，也可调用 get_my_orders 拿到订单ID（工具结果中的 id=），再调用 mock_pay 或 apply_refund。下单、支付、退款会改变订单状态：直接调用对应工具即可，系统会自动弹出确认卡片让游客点击确认，不要再次向游客索要确认。";

    private static final String SUFFIX = "请优先依据【知识库相关文档】和工具结果回答，简洁准确友好，用中文。";

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final String TOOLS_JSON = """
[
{"type":"function","function":{"name":"get_weather","description":"查询景点当前天气与3天预报","parameters":{"type":"object","properties":{"spot":{"type":"string","description":"景点名称"}},"required":[]}}},
{"type":"function","function":{"name":"get_spots","description":"查询景区内景点列表","parameters":{"type":"object","properties":{"keyword":{"type":"string","description":"景点名称关键字，可空"}},"required":[]}}},
{"type":"function","function":{"name":"get_policies","description":"查询某景点的票种与价格","parameters":{"type":"object","properties":{"spot":{"type":"string","description":"景点名称"}},"required":["spot"]}}},
{"type":"function","function":{"name":"get_slots","description":"查询某景点指定日期的分时时段与库存","parameters":{"type":"object","properties":{"spot":{"type":"string","description":"景点名称"},"date":{"type":"string","description":"日期 yyyy-MM-dd，可空"}},"required":["spot"]}}},
{"type":"function","function":{"name":"get_notices","description":"查询景区公告","parameters":{"type":"object","properties":{},"required":[]}}},
{"type":"function","function":{"name":"get_my_orders","description":"查询当前登录游客的订单列表（需登录）","parameters":{"type":"object","properties":{},"required":[]}}},
{"type":"function","function":{"name":"get_order_detail","description":"查询订单详情（需登录且为本人）","parameters":{"type":"object","properties":{"orderNo":{"type":"string"}},"required":["orderNo"]}}},
{"type":"function","function":{"name":"place_order","description":"下单购买门票（需登录）","parameters":{"type":"object","properties":{"policyId":{"type":"integer"},"slotId":{"type":"integer"},"quantity":{"type":"integer"}},"required":["policyId","slotId","quantity"]}}},
{"type":"function","function":{"name":"mock_pay","description":"对订单进行模拟支付（需登录，会扣余额）","parameters":{"type":"object","properties":{"orderId":{"type":"integer"}},"required":["orderId"]}}},
{"type":"function","function":{"name":"apply_refund","description":"对订单申请退款（需登录）","parameters":{"type":"object","properties":{"orderId":{"type":"integer"}},"required":["orderId"]}}}
]
""";

    // ==================== 运营决策 / 应急调度（国庆 10 天模拟剧本） ====================
    private static final String OPS_SYSTEM_PROMPT = "你是智慧景区的运营决策分析助手，服务于北京故宫智慧景区。你的职责是基于国庆假期前后 10 天（2026-09-28 ~ 2026-10-07）的模拟运营数据，回答运营态势、客流、销售、时段预约、退款等问题，并给出数据支撑的运营建议。所有数据以工具调用返回为准，不要编造。";
    private static final String OPS_TOOL_TIP = "你可以调用以下只读工具查询国庆 10 天模拟运营数据。用户问运营分析时，先调用 get_flow_summary / get_sales_summary / get_slot_occupancy / get_refund_stats / get_weather_forecast 获取数据，再给出结构化结论与建议。日期参数格式 yyyy-MM-dd，可省略（默认全窗口）。";
    private static final String EMERGENCY_SYSTEM_PROMPT = "你是智慧景区的应急调度助手，服务于北京故宫智慧景区。你的职责是基于国庆假期前后 10 天的模拟数据，监测客流承载、时段售罄、天气预警、退款激增等异常，给出分级（安全/警戒/危险/超限）处置建议。发布公告、加开时段属写操作：直接调用对应工具即可，系统会自动弹出确认卡由管理员确认，不要向用户重复索要确认。";
    private static final String EMERGENCY_TOOL_TIP = "你可以调用 get_emergency_scan 一键扫描指定日期（或全窗口）的异常态势，也可用 get_flow_summary / get_slot_occupancy / get_refund_stats / get_weather_forecast 查看细节。处置时给出具体建议；确需执行时调用 publish_notice（发布公告）或 dispatch_add_slot（加开时段）。";

    private static final String OPS_TOOLS_JSON = """
[
{"type":"function","function":{"name":"get_sales_summary","description":"查询区间内销售与退款汇总（订单/张票/销售额/退款率）","parameters":{"type":"object","properties":{"from":{"type":"string","description":"开始日期 yyyy-MM-dd，可空"},"to":{"type":"string","description":"结束日期 yyyy-MM-dd，可空"}},"required":[]}}},
{"type":"function","function":{"name":"get_flow_summary","description":"查询区间内客流汇总（日入园/承载率/在园峰值/峰值时刻）","parameters":{"type":"object","properties":{"from":{"type":"string","description":"开始日期 yyyy-MM-dd，可空"},"to":{"type":"string","description":"结束日期 yyyy-MM-dd，可空"}},"required":[]}}},
{"type":"function","function":{"name":"get_slot_occupancy","description":"查询区间内分时时段预约率与售罄情况","parameters":{"type":"object","properties":{"from":{"type":"string","description":"开始日期 yyyy-MM-dd，可空"},"to":{"type":"string","description":"结束日期 yyyy-MM-dd，可空"}},"required":[]}}},
{"type":"function","function":{"name":"get_refund_stats","description":"查询区间内退款率与退款申请量，标记激增","parameters":{"type":"object","properties":{"from":{"type":"string","description":"开始日期 yyyy-MM-dd，可空"},"to":{"type":"string","description":"结束日期 yyyy-MM-dd，可空"}},"required":[]}}},
{"type":"function","function":{"name":"get_weather_forecast","description":"查询国庆 10 天天气与预警","parameters":{"type":"object","properties":{"days":{"type":"integer","description":"可空"}},"required":[]}}}
]
""";

    private static final String EMERGENCY_TOOLS_JSON = """
[
{"type":"function","function":{"name":"get_flow_summary","description":"查询区间内客流汇总（日入园/承载率/在园峰值）","parameters":{"type":"object","properties":{"from":{"type":"string"},"to":{"type":"string"}},"required":[]}}},
{"type":"function","function":{"name":"get_slot_occupancy","description":"查询区间内分时时段预约率与售罄情况","parameters":{"type":"object","properties":{"from":{"type":"string"},"to":{"type":"string"}},"required":[]}}},
{"type":"function","function":{"name":"get_refund_stats","description":"查询区间内退款率与退款申请量，标记激增","parameters":{"type":"object","properties":{"from":{"type":"string"},"to":{"type":"string"}},"required":[]}}},
{"type":"function","function":{"name":"get_weather_forecast","description":"查询国庆 10 天天气与预警","parameters":{"type":"object","properties":{"days":{"type":"integer"}},"required":[]}}},
{"type":"function","function":{"name":"get_emergency_scan","description":"一键扫描国庆 10 天（或指定日期）应急异常：承载率四区/时段售罄/天气预警/退款激增，返回分级处置建议","parameters":{"type":"object","properties":{"date":{"type":"string","description":"指定日期 yyyy-MM-dd，可空（空=全窗口）"}},"required":[]}}},
{"type":"function","function":{"name":"publish_notice","description":"发布应急/运营公告（写操作，需管理员确认）","parameters":{"type":"object","properties":{"title":{"type":"string","description":"公告标题"},"content":{"type":"string","description":"公告内容"}},"required":["content"]}}},
{"type":"function","function":{"name":"dispatch_add_slot","description":"调度加开分时时段（写操作，需管理员确认）","parameters":{"type":"object","properties":{"policyId":{"type":"integer","description":"票种ID"},"date":{"type":"string","description":"日期 yyyy-MM-dd"},"startHour":{"type":"integer","description":"开始小时，如 16"},"quota":{"type":"integer","description":"配额"}},"required":["policyId"]}}}
]
""";

    private static final Set<String> EMERGENCY_WRITE_TOOLS = new HashSet<>(Arrays.asList("publish_notice", "dispatch_add_slot"));

    @Autowired(required = false) private NationalDayPlaybookService playbook;
    @Autowired(required = false) private VramMonitor vramMonitor;
    @Autowired(required = false) private ScenicSpotRepository spotRepository;

    /** 并发限 1：单模型共享，请求排队串行（最终形态：并发 >1 排队串行） */
    private final Semaphore agentLock = new Semaphore(1);
    @Autowired(required = false) private TicketPolicyRepository policyRepository;
    @Autowired(required = false) private WeatherService weatherService;
    @Autowired(required = false) private KnowledgeDocRepository docRepository;
    @Autowired(required = false) private TimeSlotRepository slotRepository;
    @Autowired(required = false) private AgentToolExecutor toolExecutor;
    @Autowired private ObjectMapper objectMapper;

    @Value("${agent.ollama-url:http://localhost:11434}") private String ollamaUrl;
    @Value("${agent.model:qwen2.5:3b}") private String model;
    @Value("${agent.embed-model:nomic-embed-text}") private String embedModel;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final Set<String> WRITE_TOOLS = new HashSet<>(Arrays.asList("place_order", "mock_pay", "apply_refund"));
    /** 多轮会话记忆：key = u:{userId} 或 s:{sessionId}，value = 最近若干轮 user/assistant 消息 */
    private final Map<String, List<Map<String, Object>>> sessionMemory = new ConcurrentHashMap<>();

    /** 常规对话：调用工具、循环直到得到答案或需确认；写操作被“谎称成功”时强制纠错一轮 */
    public Map<String, Object> chat(String question, Long userId, String sessionId) throws Exception {
        return chat(question, userId, sessionId, null);
    }

    /** 对话（支持角色）：role = tourist / ops / emergency；缺省按关键词自动路由 */
    public Map<String, Object> chat(String question, Long userId, String sessionId, String role) throws Exception {
        if (!agentLock.tryAcquire(30, TimeUnit.SECONDS)) {
            return busyAnswer("当前智能体请求较多，请稍后再试。");
        }
        try {
            return chatInner(question, userId, sessionId, role);
        } finally {
            agentLock.release();
        }
    }

    private Map<String, Object> chatInner(String question, Long userId, String sessionId, String role) throws Exception {
        String r = resolveRole(question, role);
        // 超限区（>90%）兜底：不调 LLM，降级为规则问答提示
        if (vramMonitor != null && vramMonitor.zone() == VramMonitor.OVER) {
            Map<String, Object> deg = new LinkedHashMap<>();
            deg.put("type", "answer");
            deg.put("content", "当前显存超限（>90%），智能体已降级。请重启 Ollama 释放显存，或关闭占用显存的应用（如浏览器 3D/桌面程序）后再试。");
            deg.put("steps", new ArrayList<>());
            deg.put("actions", new ArrayList<>());
            deg.put("resource", resourceInfo());
            return deg;
        }
        Set<String> writeTools = writeToolsFor(r);
        String memKey = memoryKey(userId, sessionId);
        List<Map<String, Object>> history = sessionMemory.computeIfAbsent(memKey, k -> new ArrayList<>());
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(systemMsg(question, r));
        messages.addAll(history);
        messages.add(userMsg(question));
        boolean writeToolCalled = false;
        boolean hasConfirm = false;
        Map<String, Object> pending = null;
        List<Map<String, Object>> steps = new ArrayList<>();
        for (int round = 0; round < 4; round++) {
            JsonNode resp = chatApi(messages, true, r);
            JsonNode msg = resp.path("message");
            JsonNode calls = msg.path("tool_calls");
            if (calls.isArray() && calls.size() > 0) {
                boolean hasWrite = false;
                Map<String, Object> cur = null;
                for (JsonNode call : calls) {
                    String name = call.path("function").path("name").asText("");
                    Map<String, Object> args = parseArgs(call.path("function").path("arguments"));
                    if (writeTools.contains(name)) {
                        writeToolCalled = true;
                        hasWrite = true;
                        // 修正占位参数：支付/退款时若参数无效，用问题中的订单号或最新订单兜底
                        if (("mock_pay".equals(name) || "apply_refund".equals(name)) && toolExecutor != null) {
                            args = toolExecutor.correctOrderArgs(question, args, userId);
                        }
                        cur = new LinkedHashMap<>();
                        cur.put("type", "confirm");
                        cur.put("action", name);
                        cur.put("params", args);
                        cur.put("question", question);
                        cur.put("summary", toolExecutor == null ? "" : toolExecutor.preview(name, args));
                        steps.add(stepWithParams(name, "need_confirm", String.valueOf(cur.get("summary")), args));
                    } else {
                        String result = toolExecutor == null ? "工具不可用" : toolExecutor.execute(name, args, userId, r);
                        steps.add(step(name, "done", result));
                        Map<String, Object> asst = new LinkedHashMap<>();
                        asst.put("role", "assistant");
                        asst.put("content", "");
                        asst.put("tool_calls", List.of(call));
                        Map<String, Object> tool = new LinkedHashMap<>();
                        tool.put("role", "tool");
                        tool.put("content", truncateTool(result));
                        messages.add(asst);
                        messages.add(tool);
                    }
                }
                if (hasWrite) {
                    // 危险操作不执行，返回确认卡片；执行结果由 confirm() 写入记忆
                    hasConfirm = true;
                    pending = cur;
                    break;
                }
                continue;
            }
            String content = msg.path("content").asText("");
            if (content.isEmpty()) content = "我还在整理信息，请换个方式再问我一次，比如“故宫门票多少钱”或“帮我买2张故宫成人票”。";
            // 纠错：用户意图是写操作，模型却未调用任何工具就声称完成 → 强制再试一轮
            if (writeIntent(question, r) && !writeToolCalled) {
                messages.add(userMsg("（系统提醒：你刚才的回答声称完成了操作，但你并没有调用任何工具。请立即调用相应工具真正执行：支付请调用 mock_pay，退款请调用 apply_refund，下单请调用 place_order；参数从对话历史或 get_my_orders 获取。若确实无法执行，请如实说明原因，不要谎称成功。）"));
                continue;
            }
            Map<String, Object> answer = new LinkedHashMap<>();
            answer.put("type", "answer");
            answer.put("content", content);
            answer.put("steps", steps);
            answer.put("actions", buildActions(steps, question, r));
            answer.put("resource", resourceInfo());
            appendMemory(history, question, content);
            return answer;
        }
        if (hasConfirm && pending != null) {
            pending.put("steps", steps);
            pending.put("actions", buildActions(steps, question, r));
            return pending;
        }
        // 终极兜底：模型仍未调用工具时，按问题自动推断写操作（支付/退款 + 订单号或最新订单）
        if (isTourist(r) && writeIntent(question, r) && !writeToolCalled && toolExecutor != null) {
            Map<String, Object> auto = toolExecutor.autoConfirm(question, userId);
            if (auto != null) return auto;
        }
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("type", "answer");
        List<Map<String, Object>> acts = buildActions(steps, question, r);
        String fc = "处理步骤较多，请稍后再试。";
        if (!acts.isEmpty()) fc = "已自动为你操作页面：定位故宫 → 进入详情 → 购票选择 → 选中时段。请在下单确认页核对数量后提交订单。";
        answer.put("content", fc);
        answer.put("steps", steps);
        answer.put("actions", acts);
        answer.put("resource", resourceInfo());
        return answer;
    }

    /** 生成一条执行步骤 */
    private Map<String, Object> step(String tool, String status, String summary) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tool", tool);
        m.put("status", status);
        m.put("summary", summary == null ? "" : (summary.length() > 60 ? summary.substring(0, 60) : summary));
        return m;
    }

    /** 生成一条带参数的执行步骤（供动作映射提取 slotId 等） */
    private Map<String, Object> stepWithParams(String tool, String status, String summary, Map<String, Object> params) {
        Map<String, Object> m = step(tool, status, summary);
        if (params != null && !params.isEmpty()) m.put("params", params);
        return m;
    }

    /** 根据本次工具调用轨迹 + 用户问题，生成前端页面动作（确定性映射，不依赖模型发挥） */
    private List<Map<String, Object>> buildActions(List<Map<String, Object>> steps, String question, String role) {
        if (!isTourist(role)) return new ArrayList<>();   // 管理端运营/应急不产生游客页面动作
        List<Map<String, Object>> actions = new ArrayList<>();
        Set<String> tools = new HashSet<>();
        Long slotId = null;
        Long policyId = null;
        Integer qty = null;
        for (Map<String, Object> s : steps) {
            String t = String.valueOf(s.get("tool"));
            tools.add(t);
            if ("place_order".equals(t) && s.get("params") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> p = (Map<String, Object>) s.get("params");
                slotId = toLong(p.get("slotId"));
                policyId = toLong(p.get("policyId"));
                qty = toInt(p.get("quantity"));
            }
        }
        if (qty == null && question != null) {
            java.util.regex.Matcher qm = java.util.regex.Pattern.compile("(\\d+)\\s*张").matcher(question);
            if (qm.find()) {
                try { qty = Integer.parseInt(qm.group(1)); } catch (Exception ignored) {}
            }
        }
        boolean buyIntent = question != null
            && (question.contains("买") || question.contains("下单") || question.contains("预约") || question.contains("购"));
        if (tools.contains("get_my_orders")) {
            actions.add(pageAction("goto_orders", null, "打开我的订单"));
        } else if (buyIntent && (tools.contains("get_policies") || tools.contains("get_slots") || tools.contains("place_order"))) {
            // ===== 逐步自动购票链：①定位 -> ②进详情页 -> ③切购票tab -> ④选时段填数量 =====
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("spot", "故宫");
            actions.add(pageAction("focus_spot", f, "① 定位故宫（3D 地球）"));
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("spotId", 1);
            actions.add(pageAction("goto_spot", g, "② 进入故宫详情页"));
            Map<String, Object> st = new LinkedHashMap<>();
            st.put("tab", "ticket");
            actions.add(pageAction("switch_tab", st, "③ 切换到购票选择"));
            if (slotId == null && policyId != null) slotId = firstSlotOfPolicy(policyId);
            if (slotId == null) slotId = firstSlotOfSpot(1L);
            if (slotId != null) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("slotId", slotId);
                s.put("quantity", qty == null ? 1 : qty);
                actions.add(pageAction("select_slot", s, "④ 选择时段并填写数量"));
            }
        } else {
            // 仅定位/天气/浏览类意图才聚焦（避免纯查询打断用户当前页面）
            boolean locateIntent = question != null && (question.contains("定位") || question.contains("找到")
                || question.contains("在哪") || question.contains("地图") || question.contains("天气")
                || question.contains("看看") || question.contains("去"));
            String spot = detectSpot(question);
            if (spot != null && locateIntent) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("spot", spot);
                actions.add(pageAction("focus_spot", payload, "定位「" + spot + "」（3D 地球）"));
            }
        }
        return actions;
    }

    private Map<String, Object> pageAction(String op, Map<String, Object> payload, String label) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("op", op);
        m.put("label", label == null ? "" : label);
        m.put("payload", payload == null ? new LinkedHashMap<>() : payload);
        return m;
    }

    /** 从问题里识别景点（目前支持故宫），返回 null 表示不聚焦 */
    private String detectSpot(String q) {
        if (q == null) return null;
        if (q.contains("故宫")) return "故宫";
        return null;
    }

    private Long toLong(Object o) {
        try { return o == null ? null : Long.valueOf(String.valueOf(o)); } catch (Exception e) { return null; }
    }

    private Integer toInt(Object o) {
        try { return o == null ? null : Integer.valueOf(String.valueOf(o)); } catch (Exception e) { return null; }
    }

    /** 取某票种最近的可用时段 id */
    private Long firstSlotOfPolicy(Long policyId) {
        if (slotRepository == null || policyId == null) return null;
        return slotRepository.findAll().stream()
            .filter(s -> s.getStatus() != null && s.getStatus() == 1 && policyId.equals(s.getPolicyId()))
            .sorted(Comparator.comparing(TimeSlot::getStartTime))
            .map(TimeSlot::getId).findFirst().orElse(null);
    }

    /** 取某景点（故宫）所有在售票种里最近的可用时段 id */
    private Long firstSlotOfSpot(Long spotId) {
        if (slotRepository == null || policyRepository == null || spotId == null) return null;
        List<Long> pids = policyRepository.findAll().stream()
            .filter(p -> spotId.equals(p.getSpotId()) && (p.getStatus() == null || p.getStatus() == 1))
            .map(TicketPolicy::getId).toList();
        return slotRepository.findAll().stream()
            .filter(s -> s.getStatus() != null && s.getStatus() == 1 && pids.contains(s.getPolicyId()))
            .sorted(Comparator.comparing(TimeSlot::getStartTime))
            .map(TimeSlot::getId).findFirst().orElse(null);
    }

    /** 判断用户问题是否表达“执行写操作”的意图（用于纠错：防止模型谎称完成） */
    private boolean writeIntent(String q, String role) {
        if (q == null) return false;
        boolean base = q.contains("支付") || q.contains("付款") || q.contains("下单")
            || q.contains("退款") || q.contains("退票") || q.contains("退钱")
            || q.contains("帮我买") || q.contains("帮我订") || q.contains("购买");
        if (isEmergency(role)) {
            return base || q.contains("发布") || q.contains("公告") || q.contains("加开") || q.contains("调度") || q.contains("通知");
        }
        return base;
    }

    // ==================== 角色路由 ====================
    private String resolveRole(String question, String role) {
        if (role != null && !role.isBlank()) return role.trim();
        if (question != null) {
            String q = question;
            boolean emg = q.contains("异常") || q.contains("预警") || q.contains("超载") || q.contains("超限") || q.contains("限流")
                || q.contains("突发") || q.contains("风险") || q.contains("预案") || q.contains("告警") || q.contains("应急")
                || q.contains("有没有问题") || q.contains("安全隐患");
            if (emg) return "emergency";
            boolean ops = q.contains("运营") || q.contains("客流分析") || q.contains("销售") || q.contains("营收") || q.contains("复盘")
                || q.contains("卖了多少") || q.contains("占比") || q.contains("日报") || q.contains("销量") || q.contains("数据分析")
                || q.contains("退款率") || q.contains("哪个时段");
            if (ops) return "ops";
        }
        return "tourist";
    }

    private boolean isTourist(String r) { return !"ops".equals(r) && !"emergency".equals(r); }
    private boolean isOps(String r) { return "ops".equals(r); }
    private boolean isEmergency(String r) { return "emergency".equals(r); }

    private String toolsJsonFor(String r) {
        if (isOps(r)) return OPS_TOOLS_JSON;
        if (isEmergency(r)) return EMERGENCY_TOOLS_JSON;
        return TOOLS_JSON;
    }

    private Set<String> writeToolsFor(String r) {
        if (isEmergency(r)) return EMERGENCY_WRITE_TOOLS;
        return WRITE_TOOLS;
    }

    private String playbookContext() {
        return playbook == null ? "（模拟数据未加载）" : playbook.overview();
    }

    private String memoryKey(Long userId, String sessionId) {
        if (userId != null) return "u:" + userId;
        return "s:" + (sessionId == null || sessionId.isBlank() ? "anon" : sessionId);
    }

    /** 追加一轮对话到记忆，最多保留最近 5 轮（10 条消息） */
    private void appendMemory(List<Map<String, Object>> history, String question, String answer) {
        history.add(userMsg(question));
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("role", "assistant");
        a.put("content", answer);
        history.add(a);
        while (history.size() > 10) history.remove(0);
    }

    /** 确认后执行危险操作，然后生成最终回答 */
    public Map<String, Object> confirm(String question, String action, Map<String, Object> params, Long userId, String sessionId) throws Exception {
        return confirm(question, action, params, userId, sessionId, null);
    }

    /** 确认并执行写操作（支持角色） */
    public Map<String, Object> confirm(String question, String action, Map<String, Object> params, Long userId, String sessionId, String role) throws Exception {
        if (!agentLock.tryAcquire(30, TimeUnit.SECONDS)) {
            return busyAnswer("当前智能体请求较多，请稍后再试。");
        }
        try {
            return confirmInner(question, action, params, userId, sessionId, role);
        } finally {
            agentLock.release();
        }
    }

    private Map<String, Object> confirmInner(String question, String action, Map<String, Object> params, Long userId, String sessionId, String role) throws Exception {
        if (userId == null) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("type", "answer");
            r.put("content", "请先登录再执行该操作");
            return r;
        }
        String rr = resolveRole(question, role);
        String memKey = memoryKey(userId, sessionId);
        List<Map<String, Object>> history = sessionMemory.computeIfAbsent(memKey, k -> new ArrayList<>());
        String result = toolExecutor == null ? "工具不可用" : toolExecutor.execute(action, params, userId, rr);
        String content;
        if (vramMonitor != null && vramMonitor.zone() == VramMonitor.OVER) {
            // 超限区：跳过 LLM 润色，直接返回执行结果
            content = "操作已执行：" + result;
        } else {
            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(systemMsg(question, rr));
            messages.addAll(history);
            messages.add(userMsg(question));
            Map<String, Object> note = new LinkedHashMap<>();
            note.put("role", "user");
            note.put("content", "（已执行" + action + "，结果：" + result + "）请给游客一个简洁、友好的结果说明。");
            messages.add(note);
            JsonNode resp = chatApi(messages, false, rr);
            content = resp.path("message").path("content").asText("");
            if (content.isEmpty()) content = "操作已完成，请查看订单。";
        }
        appendMemory(history, question, content);
        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(step(action, "done", result));
        List<Map<String, Object>> actions = new ArrayList<>();
        actions.add(pageAction("goto_orders", null, "打开我的订单"));
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("type", "answer");
        answer.put("content", content);
        answer.put("steps", steps);
        answer.put("actions", actions);
        answer.put("resource", resourceInfo());
        return answer;
    }

    private Map<String, Object> systemMsg(String question, String role) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", "system");
        if (isOps(role)) {
            m.put("content", OPS_SYSTEM_PROMPT + "\n\n" + playbookContext() + "\n\n" + OPS_TOOL_TIP + "\n\n" + SUFFIX);
        } else if (isEmergency(role)) {
            m.put("content", EMERGENCY_SYSTEM_PROMPT + "\n\n" + playbookContext() + "\n\n" + EMERGENCY_TOOL_TIP + "\n\n" + SUFFIX);
        } else {
            m.put("content", SYSTEM_PROMPT + "\n\n" + buildContext(question) + "\n\n" + TOOL_TIP + "\n\n" + SUFFIX);
        }
        return m;
    }

    private Map<String, Object> userMsg(String question) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", "user");
        m.put("content", question);
        return m;
    }

    private JsonNode chatApi(List<Map<String, Object>> messages, boolean withTools, String role) throws Exception {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("model", model);
        req.put("messages", messages);
        if (withTools) req.put("tools", objectMapper.readTree(toolsJsonFor(role)));
        req.put("stream", false);
        Map<String, Object> opts = new LinkedHashMap<>();
        opts.put("temperature", 0.2);
        // 危险区(≥85%)收紧单请求 max_tokens，警戒区及以上限 512，避免长回答超过 120s 超时
        int zone = vramMonitor == null ? VramMonitor.SAFE : vramMonitor.zone();
        opts.put("num_predict", zone >= VramMonitor.DANGER ? 256 : 512);
        req.put("options", opts);
        String json = objectMapper.writeValueAsString(req);
        HttpRequest r = HttpRequest.newBuilder()
                .uri(URI.create(ollamaUrl + "/api/chat"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = http.send(r, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(resp.body());
    }

    private Map<String, Object> parseArgs(JsonNode node) {
        if (node == null || node.isNull()) return new LinkedHashMap<>();
        if (node.isObject()) {
            Map<String, Object> m = new LinkedHashMap<>();
            node.fields().forEachRemaining(e -> m.put(e.getKey(), scalarValue(e.getValue())));
            return m;
        }
        if (node.isTextual()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = objectMapper.readValue(node.asText(), Map.class);
                return m == null ? new LinkedHashMap<>() : m;
            } catch (Exception e) {
                return new LinkedHashMap<>();
            }
        }
        return new LinkedHashMap<>();
    }

    /** 把 JsonNode 标量转成 Java 基本类型，保持数值/布尔/字符串类型 */
    private Object scalarValue(JsonNode n) {
        if (n.isNumber()) {
            if (n.isIntegralNumber()) return n.longValue();
            double d = n.doubleValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) return (long) d;
            return d;
        }
        if (n.isBoolean()) return n.booleanValue();
        if (n.isTextual()) return n.asText();
        return n.toString();
    }

    /** 构建上下文：系统数据 + 实时天气 + 知识库向量检索 top-5 */
    private String buildContext(String question) {
        StringBuilder sb = new StringBuilder();
        sb.append("【系统内景点与门票】").append("\n");
        if (spotRepository != null) {
            for (ScenicSpot s : spotRepository.findAll()) {
                sb.append("景点：").append(s.getName()).append("，")
                  .append(s.getDescription() == null ? "" : s.getDescription()).append("\n");
            }
        }
        Set<Long> guPolicyIds = new HashSet<>();
        if (policyRepository != null) {
            for (TicketPolicy p : policyRepository.findAll()) {
                if (p.getStatus() != null && p.getStatus() != 1) continue;
                boolean isGu = spotRepository != null && spotRepository.findAll().stream()
                    .anyMatch(sp -> p.getSpotId().equals(sp.getId()) && sp.getName().contains("故宫"));
                if (!isGu) continue;
                guPolicyIds.add(p.getId());
                sb.append("票种：").append(p.getName()).append("，").append("价格 ").append(p.getPrice()).append(" 元，id=").append(p.getId()).append("\n");
            }
        }
        if (slotRepository != null) {
            List<TimeSlot> upcoming = slotRepository.findAll().stream()
                .filter(s -> s.getStatus() != null && s.getStatus() == 1)
                .filter(s -> guPolicyIds.contains(s.getPolicyId()))
                .sorted(Comparator.comparing(TimeSlot::getStartTime))
                .toList();
            if (!upcoming.isEmpty()) {
                sb.append("\n").append("【可预约时段】").append("\n");
                int shown = 0;
                for (TimeSlot s : upcoming) {
                    if (shown >= 8) break;
                    String polName = policyRepository.findById(s.getPolicyId()).map(TicketPolicy::getName).orElse("");
                    sb.append("时段id=").append(s.getId())
                      .append("，票种id=").append(s.getPolicyId()).append("（").append(polName).append("）")
                      .append("，").append(DT.format(s.getStartTime())).append(" ~ ").append(DT.format(s.getEndTime()))
                      .append("，库存").append(s.getQuota()).append("\n");
                    shown++;
                }
            }
        }
        try {
            if (weatherService != null) {
                WeatherPoint pt = new WeatherPoint();
                pt.setName("故宫");
                pt.setLat(39.9163);
                pt.setLng(116.3972);
                WeatherVO w = weatherService.getWeather(pt);
                if (w != null) {
                    sb.append("\n").append("【故宫实时天气】").append(w.getText()).append("，温度 ").append(w.getTemp()).append("℃")
                      .append("，体感 ").append(w.getFeelsLike()).append("，湿度 ").append(w.getHumidity())
                      .append("，风力 ").append(w.getWind())
                      .append("，降雨概率 ").append(w.getRainProb() == null ? "未知" : w.getRainProb() + "%").append("\n");
                }
            }
        } catch (Exception ignored) {
        }
        try {
            if (docRepository != null) {
                List<KnowledgeDoc> docs = docRepository.findAll();
                if (!docs.isEmpty()) {
                    float[] qv = embed(question);
                    List<Object[]> scored = new ArrayList<>();
                    for (KnowledgeDoc d : docs) {
                        float[] dv = embed(d.getTitle() + "\n" + d.getContent());
                        scored.add(new Object[]{cosine(qv, dv), d});
                    }
                    scored.sort((a, b) -> Double.compare((double) b[0], (double) a[0]));
                    sb.append("\n").append("【知识库相关文档】").append("\n");
                    int top = Math.min(ragTopK(), scored.size());
                    for (int i = 0; i < top; i++) {
                        KnowledgeDoc d = (KnowledgeDoc) scored.get(i)[1];
                        sb.append("文档：").append(d.getTitle()).append("\n").append(d.getContent()).append("\n");
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return sb.toString();
    }

    private float[] embed(String text) throws Exception {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("model", embedModel);
        req.put("prompt", text);
        Map<String, Object> opts = new LinkedHashMap<>();
        opts.put("num_gpu", 0);   // 嵌入强制 CPU：RAG 不占独显（最终形态：nomic ≈0 显存）
        req.put("options", opts);
        String json = objectMapper.writeValueAsString(req);
        HttpRequest r = HttpRequest.newBuilder()
                .uri(URI.create(ollamaUrl + "/api/embeddings"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = http.send(r, HttpResponse.BodyHandlers.ofString());
        JsonNode node = objectMapper.readTree(resp.body());
        JsonNode emb = node.path("embedding");
        float[] arr = new float[emb.size()];
        for (int i = 0; i < emb.size(); i++) arr[i] = (float) emb.get(i).asDouble();
        return arr;
    }

    /** RAG top-k：警戒区及以下 3，危险区及以上收紧到 2（最终形态：top-k=3） */
    private int ragTopK() {
        int zone = vramMonitor == null ? VramMonitor.SAFE : vramMonitor.zone();
        return zone >= VramMonitor.DANGER ? 2 : 3;
    }

    /** 工具结果截断：只把必要字段回传模型（危险区"工具返回精简"） */
    private String truncateTool(String s) {
        if (s == null) return "";
        if (s.length() <= 900) return s;
        return s.substring(0, 900) + "…（工具结果过长已截断）";
    }

    /** 并发繁忙 / 资源超限时的降级回答 */
    private Map<String, Object> busyAnswer(String msg) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("type", "answer");
        r.put("content", msg);
        r.put("steps", new ArrayList<>());
        r.put("actions", new ArrayList<>());
        r.put("resource", resourceInfo());
        return r;
    }

    /** 显存资源快照（随回答返回，供前端展示四区状态） */
    private Map<String, Object> resourceInfo() {
        if (vramMonitor == null) return new LinkedHashMap<>();
        return vramMonitor.info();
    }

    private double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb) + 1e-9);
    }
}
