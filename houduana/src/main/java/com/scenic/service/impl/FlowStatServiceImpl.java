package com.scenic.service.impl;

import com.scenic.entity.FlowStat;
import com.scenic.entity.Order;
import com.scenic.entity.TicketPolicy;
import com.scenic.entity.TimeSlot;
import com.scenic.repository.FlowStatRepository;
import com.scenic.repository.OrderRepository;
import com.scenic.repository.ScenicSpotRepository;
import com.scenic.repository.TicketPolicyRepository;
import com.scenic.repository.TimeSlotRepository;
import com.scenic.service.FlowStatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 客流统计服务：
 * 基于票务销售、分时预约、核销全链路数据计算多维度指标，
 * 支持管理端 Dashboard 统计卡片、ECharts 图表与实时客流数据。
 */
@Service
public class FlowStatServiceImpl implements FlowStatService {

    @Autowired
    private FlowStatRepository flowStatRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private TicketPolicyRepository ticketPolicyRepository;

    @Autowired
    private ScenicSpotRepository scenicSpotRepository;

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Override
    public Map<String, Object> getStats() {
        Date todayStart = atStartOfDay(LocalDate.now());
        Date todayEnd = atEndOfDay(LocalDate.now());
        Date now = new Date();

        // 今日订单量
        int todayOrders = orderRepository.findByCreateTimeBetween(todayStart, todayEnd).size();

        // 今日入园人数（已核销）
        List<Order> enteredToday = orderRepository.findByStatusAndUseTimeBetween(2, todayStart, todayEnd);
        int todayEntered = sumQuantity(enteredToday);

        // 当前在园人数：已支付（未核销） + 已核销且尚在时段内
        int currentVisitors = inParkVisitors(now);

        // 累计游客（已支付 + 已核销）
        long totalVisitors = orderRepository.countByStatusGreaterThanEqual(1);

        // 近 7 日入园趋势
        List<String> dates = new ArrayList<>();
        List<Integer> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            dates.add(day.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd")));
            List<Order> dayOrders = orderRepository.findByStatusAndUseTimeBetween(2, atStartOfDay(day), atEndOfDay(day));
            trend.add(sumQuantity(dayOrders));
        }

        // 各时段入园占比（今日核销订单按入园小时组）
        List<Map<String, Object>> hourlyDistribution = hourlyDistribution(enteredToday);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("todayEntered", todayEntered);
        data.put("currentVisitors", currentVisitors);
        data.put("todayOrders", todayOrders);
        data.put("totalVisitors", totalVisitors);
        data.put("dates", dates);
        data.put("trend", trend);
        data.put("hourlyDistribution", hourlyDistribution);
        return data;
    }

    @Override
    public Map<String, Object> getRealtime() {
        Map<String, Object> data = new LinkedHashMap<>();
        Date todayStart = atStartOfDay(LocalDate.now());
        Date todayEnd = atEndOfDay(LocalDate.now());
        data.put("currentVisitors", inParkVisitors(new Date()));
        data.put("todayEntered", sumQuantity(orderRepository.findByStatusAndUseTimeBetween(2, todayStart, todayEnd)));
        data.put("updateTime", new Date());
        return data;
    }

    /**
     * 核销入园后记录客流：按景点、按日维度记录到 t_flow_stat
     */
    @Override
    @Transactional
    public void recordEntry(Order order) {
        if (order == null || order.getPolicyId() == null) {
            return;
        }
        TicketPolicy policy = ticketPolicyRepository.findById(order.getPolicyId()).orElse(null);
        Long spotId = policy != null ? policy.getSpotId() : null;
        if (spotId == null) {
            return;
        }
        Date day = atStartOfDay(LocalDate.now());
        FlowStat stat = flowStatRepository.findBySpotIdAndStatTime(spotId, day).orElseGet(() -> {
            FlowStat s = new FlowStat();
            s.setSpotId(spotId);
            s.setStatTime(day);
            s.setCurrentVisitors(0);
            s.setEnteredToday(0);
            return s;
        });
        int qty = order.getQuantity() == null ? 1 : order.getQuantity();
        stat.setEnteredToday((stat.getEnteredToday() == null ? 0 : stat.getEnteredToday()) + qty);
        stat.setCurrentVisitors((stat.getCurrentVisitors() == null ? 0 : stat.getCurrentVisitors()) + qty);
        flowStatRepository.save(stat);
    }

    // ====== 私有方法 ======

    /** 当前在园人数：已支付订单 + 已核销且时段未结束 */
    private int inParkVisitors(Date now) {
        List<Order> paid = orderRepository.findByStatus(1);
        List<Order> used = orderRepository.findByStatus(2);
        Set<Long> slotIds = new HashSet<>();
        for (Order o : paid) {
            if (o.getSlotId() != null) slotIds.add(o.getSlotId());
        }
        for (Order o : used) {
            if (o.getSlotId() != null) slotIds.add(o.getSlotId());
        }
        Map<Long, TimeSlot> slotMap = slotIds.isEmpty() ? Collections.emptyMap()
                : timeSlotRepository.findAllById(slotIds).stream()
                        .collect(Collectors.toMap(TimeSlot::getId, s -> s, (a, b) -> a));

        int count = 0;
        for (Order o : paid) {
            TimeSlot slot = slotMap.get(o.getSlotId());
            if (slot != null && slot.getEndTime() != null && slot.getEndTime().isAfter(LocalDateTime.now())) {
                count += o.getQuantity() == null ? 0 : o.getQuantity();
            }
        }
        for (Order o : used) {
            TimeSlot slot = slotMap.get(o.getSlotId());
            if (slot != null && slot.getEndTime() != null && slot.getEndTime().isAfter(LocalDateTime.now())) {
                count += o.getQuantity() == null ? 0 : o.getQuantity();
            }
        }
        return count;
    }

    /** 按入园小时分组（2 小时一个桶） */
    private List<Map<String, Object>> hourlyDistribution(List<Order> enteredToday) {
        Map<Integer, Integer> bucket = new TreeMap<>();
        for (Order o : enteredToday) {
            if (o.getUseTime() == null) continue;
            LocalDateTime use = LocalDateTime.ofInstant(o.getUseTime().toInstant(), ZONE);
            int start = (use.getHour() / 2) * 2;
            int qty = o.getQuantity() == null ? 1 : o.getQuantity();
            bucket.put(start, bucket.getOrDefault(start, 0) + qty);
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : bucket.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("value", e.getValue());
            item.put("name", e.getKey() + "-" + (e.getKey() + 2) + "点");
            list.add(item);
        }
        return list;
    }

    private int sumQuantity(List<Order> orders) {
        return orders.stream()
                .mapToInt(o -> o.getQuantity() == null ? 1 : o.getQuantity())
                .sum();
    }

    private Date atStartOfDay(LocalDate day) {
        return Date.from(day.atStartOfDay(ZONE).toInstant());
    }

    private Date atEndOfDay(LocalDate day) {
        return Date.from(day.atTime(LocalTime.MAX).atZone(ZONE).toInstant());
    }
}
