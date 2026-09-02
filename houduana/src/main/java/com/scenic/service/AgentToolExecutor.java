package com.scenic.service;

import com.scenic.entity.*;
import com.scenic.repository.*;
import com.scenic.vo.WeatherPoint;
import com.scenic.vo.WeatherVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/** 智能体工具执行器：对接系统现有服务，把工具调用结果返回给 AI */
@Service
public class AgentToolExecutor {

    @Autowired(required = false) private WeatherService weatherService;
    @Autowired(required = false) private ScenicSpotRepository spotRepository;
    @Autowired(required = false) private TicketPolicyRepository policyRepository;
    @Autowired(required = false) private TimeSlotRepository slotRepository;
    @Autowired(required = false) private NoticeRepository noticeRepository;
    @Autowired(required = false) private OrderRepository orderRepository;
    @Autowired(required = false) private OrderService orderService;
    @Autowired(required = false) private PayService payService;
    @Autowired(required = false) private NationalDayPlaybookService playbook;
    @Autowired(required = false) private NoticeService noticeService;
    @Autowired(required = false) private TimeSlotService timeSlotService;
    @Autowired(required = false) private UserRepository userRepository;

    private final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final DateTimeFormatter D = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public String execute(String name, Map<String, Object> args, Long userId, String role) {
        try {
            switch (name) {
                case "get_weather": return weather(args);
                case "get_spots": return spots(args);
                case "get_policies": return policies(args);
                case "get_slots": return slots(args);
                case "get_notices": return notices();
                case "get_my_orders": return myOrders(userId);
                case "get_order_detail": return orderDetail(args, userId);
                case "place_order": return placeOrder(userId, args);
                case "mock_pay": return mockPay(userId, args);
                case "apply_refund": return applyRefund(userId, args);
                // ===== 运营决策 / 应急调度（国庆 10 天模拟剧本） =====
                case "get_sales_summary": return playbookOk() ? playbook.salesSummary(strArg(args, "from"), strArg(args, "to")) : "剧本数据未加载";
                case "get_flow_summary": return playbookOk() ? playbook.flowSummary(strArg(args, "from"), strArg(args, "to")) : "剧本数据未加载";
                case "get_slot_occupancy": return playbookOk() ? playbook.occupancySummary(strArg(args, "from"), strArg(args, "to")) : "剧本数据未加载";
                case "get_refund_stats": return playbookOk() ? playbook.refundSummary(strArg(args, "from"), strArg(args, "to")) : "剧本数据未加载";
                case "get_weather_forecast": return playbookOk() ? playbook.weatherForecast(intArg(args, "days")) : "剧本数据未加载";
                case "get_emergency_scan": return playbookOk() ? playbook.emergencyScan(strArg(args, "date")) : "剧本数据未加载";
                case "publish_notice": return publishNotice(args, userId, role);
                case "dispatch_add_slot": return dispatchAddSlot(args, userId, role);
                default: return "未知工具：" + name;
            }
        } catch (Exception e) {
            return "执行失败：" + e.getMessage();
        }
    }

    public String preview(String name, Map<String, Object> args) {
        try {
            switch (name) {
                case "place_order": {
                    Long policyId = longArg(args, "policyId");
                    if (policyId == null) return "请先选择票种";
                    Long slotId = resolveSlotId(args, policyId);
                    if (slotId == null) return "该票种暂无可预约时段";
                    int qty = intArg(args, "quantity");
                    if (qty <= 0) return "请填写购买数量";
                    TicketPolicy p = policyRepository.findById(policyId).orElse(null);
                    TimeSlot s = slotRepository.findById(slotId).orElse(null);
                    String spot = p == null ? "" : spotRepository.findById(p.getSpotId()).map(ScenicSpot::getName).orElse("");
                    BigDecimal total = p == null ? BigDecimal.ZERO : p.getPrice().multiply(BigDecimal.valueOf(qty));
                    String slotText = s == null ? "" : s.getStartTime().format(DT) + " - " + s.getEndTime().format(DT);
                    return "为你在" + spot + "下单" + (p == null ? "" : p.getName()) + " " + qty + "张，时段" + slotText + "，合计" + total + "元。";
                }
                case "mock_pay": {
                    Order o = resolveOrder(args);
                    if (o == null) return "订单不存在";
                    return "对订单 " + o.getOrderNo() + " 确认模拟支付，金额 " + o.getTotalAmount() + " 元。";
                }
                case "apply_refund": {
                    Order o = resolveOrder(args);
                    if (o == null) return "订单不存在";
                    return "对订单 " + o.getOrderNo() + " 申请退款，金额 " + o.getTotalAmount() + " 元。";
                }
                case "publish_notice": {
                    String title = strArg(args, "title");
                    String content = strArg(args, "content");
                    return "发布公告「" + (title == null || title.isBlank() ? "应急公告" : title) + "」：" + (content == null || content.isBlank() ? "" : content);
                }
                case "dispatch_add_slot": {
                    Long policyId = longArg(args, "policyId");
                    String date = strArg(args, "date");
                    int hour = intArg(args, "startHour");
                    int quota = intArg(args, "quota");
                    return "为票种 " + policyId + " 在 " + date + " " + (hour < 0 ? 16 : hour) + ":00 加开时段，配额 " + (quota <= 0 ? 1000 : quota);
                }
                default: return "";
            }
        } catch (Exception e) {
            return "";
        }
    }

    private String weather(Map<String, Object> args) {
        if (weatherService == null) return "天气服务不可用";
        WeatherPoint pt = new WeatherPoint();
        pt.setName("故宫");
        pt.setLat(39.9163);
        pt.setLng(116.3972);
        WeatherVO w = weatherService.getWeather(pt);
        if (w == null) return "天气数据获取失败";
        StringBuilder sb = new StringBuilder();
        sb.append(w.getName()).append("当前天气：").append(w.getText())
          .append("，温度 ").append(w.getTemp()).append("℃")
          .append("，体感 ").append(w.getFeelsLike())
          .append("，湿度 ").append(w.getHumidity())
          .append("，风力 ").append(w.getWind());
        if (w.getRainProb() != null) sb.append("，降雨概率 ").append(w.getRainProb()).append("%");
        if (w.getAlert() != null) sb.append("，警报：").append(w.getAlert());
        if (w.getClothing() != null) sb.append("，穿衣建议：").append(w.getClothing());
        return sb.toString();
    }

    private String spots(Map<String, Object> args) {
        if (spotRepository == null) return "数据库不可用";
        String kw = args == null ? null : String.valueOf(args.get("keyword"));
        StringBuilder sb = new StringBuilder("景点列表：\n");
        int n = 0;
        for (ScenicSpot s : spotRepository.findAll()) {
            if (kw != null && !kw.isBlank() && !s.getName().toLowerCase().contains(kw.toLowerCase())) continue;
            sb.append("- ").append(s.getName()).append("：").append(s.getDescription() == null ? "" : s.getDescription()).append("\n");
            n++;
        }
        if (n == 0) return "未找到景点";
        return sb.toString();
    }

    private String policies(Map<String, Object> args) {
        if (policyRepository == null) return "数据库不可用";
        String spot = args == null ? "" : String.valueOf(args.get("spot"));
        Long spotId = null;
        if (spot != null && !spot.isBlank() && spotRepository != null) {
            spotId = spotRepository.findAll().stream().filter(s -> s.getName().contains(spot)).map(ScenicSpot::getId).findFirst().orElse(null);
        }
        StringBuilder sb = new StringBuilder("票种与价格：\n");
        int n = 0;
        for (TicketPolicy p : policyRepository.findAll()) {
            if (spotId != null && !spotId.equals(p.getSpotId())) continue;
            sb.append("- ").append(p.getName()).append("：").append(p.getPrice()).append("元，库存").append(p.getTotalQuota()).append("，id=").append(p.getId());
            if (p.getRefundRule() != null && !p.getRefundRule().isBlank()) sb.append("，退改：").append(p.getRefundRule());
            sb.append("\n");
            n++;
        }
        if (n == 0) return "未找到票种";
        return sb.toString();
    }

    private String slots(Map<String, Object> args) {
        if (slotRepository == null) return "数据库不可用";
        String spot = args == null ? "" : String.valueOf(args.get("spot"));
        String dateStr = args == null ? null : String.valueOf(args.get("date"));
        Long spotId = null;
        if (spot != null && !spot.isBlank() && spotRepository != null) {
            spotId = spotRepository.findAll().stream().filter(s -> s.getName().contains(spot)).map(ScenicSpot::getId).findFirst().orElse(null);
        }
        Set<Long> policyIds = new HashSet<>();
        if (policyRepository != null) {
            for (TicketPolicy p : policyRepository.findAll()) {
                if (spotId == null || spotId.equals(p.getSpotId())) policyIds.add(p.getId());
            }
        }
        StringBuilder sb = new StringBuilder("可预约时段：\n");
        int n = 0;
        LocalDate date = null;
        if (dateStr != null && !dateStr.isBlank()) { try { date = LocalDate.parse(dateStr, D); } catch (Exception ignored) {} }
        for (TimeSlot s : slotRepository.findAll()) {
            if (!policyIds.contains(s.getPolicyId())) continue;
            if (s.getStatus() == null || s.getStatus() != 1) continue;
            if (date != null && !s.getStartTime().toLocalDate().equals(date)) continue;
            sb.append("- 时段 ").append(s.getStartTime().format(DT)).append(" ~ ").append(s.getEndTime().format(DT))
              .append("，库存").append(s.getQuota()).append("，已预").append(s.getBooked())
              .append("，id=").append(s.getId()).append("，policyId=").append(s.getPolicyId()).append("\n");
            n++;
        }
        if (n == 0) return "该日期无可预约时段";
        return sb.toString();
    }

    private String notices() {
        if (noticeRepository == null) return "公告不可用";
        StringBuilder sb = new StringBuilder("公告：\n");
        for (Notice n : noticeRepository.findAll()) {
            if (n.getStatus() == null || n.getStatus() != 1) continue;
            sb.append("- ").append(n.getTitle()).append(": ").append(n.getContent() == null ? "" : n.getContent()).append("\n");
        }
        return sb.toString();
    }

    private String myOrders(Long userId) {
        if (orderRepository == null) return "订单服务不可用";
        if (userId == null) return "未登录，请先登录再查询我的订单";
        StringBuilder sb = new StringBuilder("我的订单：\n");
        List<Order> orders = orderRepository.findAll().stream()
            .filter(o -> userId.equals(o.getUserId()))
            .sorted((a,b) -> Long.compare(b.getId(), a.getId()))
            .limit(10).collect(Collectors.toList());
        for (Order o : orders) {
            sb.append("- ").append(o.getOrderNo()).append("（id=").append(o.getId()).append("），金额").append(o.getTotalAmount())
              .append("元，状态号").append(o.getStatus())
              .append("，时间").append(o.getCreateTime() == null ? "" : o.getCreateTime().toInstant().toString()).append("\n");
        }
        return orders.isEmpty() ? "还没有订单" : sb.toString();
    }

    private String orderDetail(Map<String, Object> args, Long userId) {
        if (orderRepository == null) return "订单服务不可用";
        String no = args == null ? "" : String.valueOf(args.get("orderNo"));
        Order o = orderRepository.findByOrderNo(no).orElse(null);
        if (o == null) return "订单不存在";
        if (userId != null && !userId.equals(o.getUserId())) return "只能查询自己的订单";
        return "订单 " + o.getOrderNo() + "（id=" + o.getId() + "），金额" + o.getTotalAmount() + "元，数量" + o.getQuantity() + "，状态号" + o.getStatus() + "，下单时间" + (o.getCreateTime() == null ? "" : o.getCreateTime().toInstant().toString());
    }

    private String placeOrder(Long userId, Map<String, Object> args) {
        if (userId == null) return "未登录，不能下单";
        Long policyId = longArg(args, "policyId");
        if (policyId == null) return "下单参数缺少票种";
        Long slotId = resolveSlotId(args, policyId);
        if (slotId == null) return "该票种暂无可预约时段";
        int qty = intArg(args, "quantity");
        if (qty <= 0) return "下单数量无效";
        Order order = new Order();
        order.setPolicyId(policyId);
        order.setSlotId(slotId);
        order.setQuantity(qty);
        Order created = orderService.createOrder(order, userId);
        return "下单成功：订单号 " + created.getOrderNo() + "（id=" + created.getId() + "），金额 " + created.getTotalAmount() + " 元";
    }

    private String mockPay(Long userId, Map<String, Object> args) {
        if (userId == null) return "未登录，不能支付";
        Order o = resolveOrder(args);
        if (o == null) return "订单不存在";
        if (!userId.equals(o.getUserId())) return "只能支付自己的订单";
        com.scenic.vo.PayResult r = payService.mockConfirm(o.getId(), userId, "user");
        return "模拟支付成功：订单 " + r.getOrderNo() + "，金额 " + r.getAmount() + " 元";
    }

    private String applyRefund(Long userId, Map<String, Object> args) {
        if (userId == null) return "未登录，不能申请退款";
        Order o = resolveOrder(args);
        if (o == null) return "订单不存在";
        if (!userId.equals(o.getUserId())) return "只能操作自己的订单";
        orderService.applyRefund(o.getId(), userId);
        return "已提交退款申请，等待管理员审核";
    }

    /** 取 Long 类型参数，缺失或非法返回 null */
    private Long longArg(Map<String, Object> args, String key) {
        if (args == null || !args.containsKey(key)) return null;
        try {
            return Long.valueOf(String.valueOf(args.get(key)));
        } catch (Exception e) {
            return null;
        }
    }

    /** 取 int 类型参数，缺失或非法返回 -1 */
    private int intArg(Map<String, Object> args, String key) {
        if (args == null || !args.containsKey(key)) return -1;
        try {
            return Integer.parseInt(String.valueOf(args.get(key)));
        } catch (Exception e) {
            return -1;
        }
    }

    /** 取 slotId；缺省时选该票种最近一个可预约时段 */
    private Long resolveSlotId(Map<String, Object> args, Long policyId) {
        Long slotId = longArg(args, "slotId");
        if (slotId != null) return slotId;
        if (policyId != null && slotRepository != null) {
            return slotRepository.findAll().stream()
                .filter(s -> s.getStatus() != null && s.getStatus() == 1 && policyId.equals(s.getPolicyId()))
                .sorted(Comparator.comparing(TimeSlot::getStartTime))
                .map(TimeSlot::getId)
                .findFirst().orElse(null);
        }
        return null;
    }

    /** 订单参数兼容 orderId 与 orderNo（二者都可能是数字ID或32位订单号） */
    private Order resolveOrder(Map<String, Object> args) {
        Long id = longArg(args, "orderId");
        if (id != null) return orderRepository.findById(id).orElse(null);
        String no = args == null ? null : String.valueOf(args.get("orderId"));
        if (no != null && !no.isBlank() && !"null".equals(no)) {
            Order byNo = orderRepository.findByOrderNo(no).orElse(null);
            if (byNo != null) return byNo;
        }
        String no2 = args == null ? null : String.valueOf(args.get("orderNo"));
        if (no2 != null && !no2.isBlank() && !"null".equals(no2)) {
            Order byNo2 = orderRepository.findByOrderNo(no2).orElse(null);
            if (byNo2 != null) return byNo2;
        }
        return null;
    }

    // ==================== 运营决策 / 应急调度工具 ====================
    private boolean playbookOk() { return playbook != null && playbook.available(); }

    /** 发布应急/运营公告（写操作，仅管理员，走确认卡） */
    private String publishNotice(Map<String, Object> args, Long userId, String role) {
        if (!isAdmin(userId, role)) return "仅管理员可发布公告";
        if (noticeService == null) return "公告服务不可用";
        String title = strArg(args, "title");
        String content = strArg(args, "content");
        if (title == null || title.isBlank()) title = "应急公告";
        Notice notice = new Notice();
        notice.setTitle(title);
        notice.setContent(content == null || content.isBlank() ? "系统智能体发布的运营公告。" : content);
        notice.setPublishTime(new Date());
        notice.setStatus(1);
        Notice saved = noticeService.add(notice);
        return "公告已发布（id=" + saved.getId() + "）「" + title + "」";
    }

    /** 调度加开时段（写操作，仅管理员，走确认卡） */
    private String dispatchAddSlot(Map<String, Object> args, Long userId, String role) {
        if (!isAdmin(userId, role)) return "仅管理员可加开时段";
        if (timeSlotService == null) return "时段服务不可用";
        Long policyId = longArg(args, "policyId");
        if (policyId == null) return "加开时段缺少票种 policyId";
        if (policyRepository == null || policyRepository.findById(policyId).isEmpty()) return "票种 " + policyId + " 不存在，请先查 get_policies";
        String date = strArg(args, "date");
        if (date == null || date.isBlank()) date = LocalDate.now().toString();
        int hour = intArg(args, "startHour");
        if (hour < 0 || hour > 23) hour = 16;
        int quota = intArg(args, "quota");
        if (quota <= 0) quota = 1000;
        LocalDateTime start = LocalDate.parse(date).atTime(hour, 0);
        LocalDateTime end = start.plusHours(2);
        TimeSlot slot = new TimeSlot();
        slot.setPolicyId(policyId);
        slot.setStartTime(start);
        slot.setEndTime(end);
        slot.setQuota(quota);
        slot.setBooked(0);
        slot.setStatus(1);
        TimeSlot saved = timeSlotService.add(slot);
        return "已加开时段 id=" + saved.getId() + "（票种 " + policyId + "，" + start.format(DT) + " ~ " + end.format(DT) + "，配额 " + quota + "）";
    }

    private boolean isAdmin(Long userId, String role) {
        if (role != null && "admin".equals(role)) return true;
        if (userId != null && userRepository != null) {
            try {
                return userRepository.findById(userId).map(u -> "admin".equals(u.getRole())).orElse(false);
            } catch (Exception ignored) { }
        }
        return false;
    }

    private String strArg(Map<String, Object> args, String key) {
        if (args == null || !args.containsKey(key)) return null;
        Object v = args.get(key);
        if (v == null) return null;
        String s = String.valueOf(v);
        return s.isBlank() || "null".equals(s) ? null : s.trim();
    }

    /** 兜底：按问题自动推断写操作（支付/退款 + 订单号或最新订单），返回确认信息；无法推断返回 null */
    public Map<String, Object> autoConfirm(String question, Long userId) {
        if (userId == null || question == null) return null;
        boolean pay = question.contains("支付") || question.contains("付款");
        boolean refund = question.contains("退款") || question.contains("退票") || question.contains("退钱");
        if (!pay && !refund) return null;
        String action = pay ? "mock_pay" : "apply_refund";
        String no = extractOrderNo(question);
        Map<String, Object> params = new LinkedHashMap<>();
        if (no != null) {
            params.put("orderId", no);
        } else {
            Order latest = orderRepository.findAll().stream()
                .filter(o -> userId.equals(o.getUserId()))
                .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                .findFirst().orElse(null);
            if (latest == null) return null;
            params.put("orderId", latest.getId());
        }
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "confirm");
        p.put("action", action);
        p.put("params", params);
        p.put("question", question);
        p.put("summary", preview(action, params));
        return p;
    }

    private String extractOrderNo(String q) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[0-9a-f]{32}").matcher(q);
        return m.find() ? m.group() : null;
    }

    /** 修正支付/退款参数：模型可能给出占位/不存在的订单ID，用问题中的订单号或该用户最新订单兜底 */
    public Map<String, Object> correctOrderArgs(String question, Map<String, Object> args, Long userId) {
        Map<String, Object> fixed = new LinkedHashMap<>(args == null ? new LinkedHashMap<>() : args);
        if (resolveOrder(fixed) != null) return fixed;
        String no = extractOrderNo(question);
        if (no != null) {
            fixed.put("orderId", no);
            return fixed;
        }
        if (userId != null) {
            Order latest = orderRepository.findAll().stream()
                .filter(o -> userId.equals(o.getUserId()))
                .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                .findFirst().orElse(null);
            if (latest != null) {
                fixed.put("orderId", latest.getId());
                return fixed;
            }
        }
        return fixed;
    }
}
