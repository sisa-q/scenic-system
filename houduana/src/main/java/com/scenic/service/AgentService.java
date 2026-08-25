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

    @Autowired(required = false) private ScenicSpotRepository spotRepository;
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
        String memKey = memoryKey(userId, sessionId);
        List<Map<String, Object>> history = sessionMemory.computeIfAbsent(memKey, k -> new ArrayList<>());
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(systemMsg(question));
        messages.addAll(history);
        messages.add(userMsg(question));
        boolean writeToolCalled = false;
        boolean hasConfirm = false;
        Map<String, Object> pending = null;
        for (int round = 0; round < 4; round++) {
            JsonNode resp = chatApi(messages, true);
            JsonNode msg = resp.path("message");
            JsonNode calls = msg.path("tool_calls");
            if (calls.isArray() && calls.size() > 0) {
                boolean hasWrite = false;
                Map<String, Object> cur = null;
                for (JsonNode call : calls) {
                    String name = call.path("function").path("name").asText("");
                    Map<String, Object> args = parseArgs(call.path("function").path("arguments"));
                    if (WRITE_TOOLS.contains(name)) {
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
                    } else {
                        String result = toolExecutor == null ? "工具不可用" : toolExecutor.execute(name, args, userId);
                        Map<String, Object> asst = new LinkedHashMap<>();
                        asst.put("role", "assistant");
                        asst.put("content", "");
                        asst.put("tool_calls", List.of(call));
                        Map<String, Object> tool = new LinkedHashMap<>();
                        tool.put("role", "tool");
                        tool.put("content", result);
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
            if (writeIntent(question) && !writeToolCalled) {
                messages.add(userMsg("（系统提醒：你刚才的回答声称完成了操作，但你并没有调用任何工具。请立即调用相应工具真正执行：支付请调用 mock_pay，退款请调用 apply_refund，下单请调用 place_order；参数从对话历史或 get_my_orders 获取。若确实无法执行，请如实说明原因，不要谎称成功。）"));
                continue;
            }
            Map<String, Object> answer = new LinkedHashMap<>();
            answer.put("type", "answer");
            answer.put("content", content);
            appendMemory(history, question, content);
            return answer;
        }
        if (hasConfirm && pending != null) return pending;
        // 终极兜底：模型仍未调用工具时，按问题自动推断写操作（支付/退款 + 订单号或最新订单）
        if (writeIntent(question) && !writeToolCalled && toolExecutor != null) {
            Map<String, Object> auto = toolExecutor.autoConfirm(question, userId);
            if (auto != null) return auto;
        }
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("type", "answer");
        answer.put("content", "处理步骤较多，请稍后再试。");
        return answer;
    }

    /** 判断用户问题是否表达“执行写操作”的意图（用于纠错：防止模型谎称完成） */
    private boolean writeIntent(String q) {
        if (q == null) return false;
        return q.contains("支付") || q.contains("付款") || q.contains("下单")
            || q.contains("退款") || q.contains("退票") || q.contains("退钱")
            || q.contains("帮我买") || q.contains("帮我订") || q.contains("购买");
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
        if (userId == null) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("type", "answer");
            r.put("content", "请先登录再执行该操作");
            return r;
        }
        String memKey = memoryKey(userId, sessionId);
        List<Map<String, Object>> history = sessionMemory.computeIfAbsent(memKey, k -> new ArrayList<>());
        String result = toolExecutor == null ? "工具不可用" : toolExecutor.execute(action, params, userId);
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(systemMsg(question));
        messages.addAll(history);
        messages.add(userMsg(question));
        Map<String, Object> note = new LinkedHashMap<>();
        note.put("role", "user");
        note.put("content", "（已执行" + action + "，结果：" + result + "）请给游客一个简洁、友好的结果说明。");
        messages.add(note);
        JsonNode resp = chatApi(messages, false);
        String content = resp.path("message").path("content").asText("");
        if (content.isEmpty()) content = "操作已完成，请查看订单。";
        appendMemory(history, question, content);
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("type", "answer");
        answer.put("content", content);
        return answer;
    }

    private Map<String, Object> systemMsg(String question) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", "system");
        m.put("content", SYSTEM_PROMPT + "\n\n" + buildContext(question) + "\n\n" + TOOL_TIP + "\n\n" + SUFFIX);
        return m;
    }

    private Map<String, Object> userMsg(String question) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", "user");
        m.put("content", question);
        return m;
    }

    private JsonNode chatApi(List<Map<String, Object>> messages, boolean withTools) throws Exception {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("model", model);
        req.put("messages", messages);
        if (withTools) req.put("tools", objectMapper.readTree(TOOLS_JSON));
        req.put("stream", false);
        Map<String, Object> opts = new LinkedHashMap<>();
        opts.put("temperature", 0.2);
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
                    int top = Math.min(5, scored.size());
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
