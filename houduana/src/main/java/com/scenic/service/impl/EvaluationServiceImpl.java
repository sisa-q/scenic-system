package com.scenic.service.impl;

import com.scenic.entity.Evaluation;
import com.scenic.entity.Order;
import com.scenic.entity.ScenicSpot;
import com.scenic.entity.TicketPolicy;
import com.scenic.repository.EvaluationRepository;
import com.scenic.repository.OrderRepository;
import com.scenic.repository.ScenicSpotRepository;
import com.scenic.repository.TicketPolicyRepository;
import com.scenic.service.EvaluationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 评价服务：
 * 支持游客对已完成的行程进行评分与文字评价，
 * 通过简单词典匹配算法计算情感得分，管理端分页展示。
 */
@Service
public class EvaluationServiceImpl implements EvaluationService {

    @Autowired
    private EvaluationRepository evaluationRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TicketPolicyRepository ticketPolicyRepository;

    @Autowired
    private ScenicSpotRepository scenicSpotRepository;

    @Override
    public Map<String, Object> listAll(int page, int size, String orderNo, String spotName, Integer rating,
                                       String startDate, String endDate) {
        List<Evaluation> all = evaluationRepository.findAll();
        fillOrderInfo(all);

        // 过滤
        List<Evaluation> filtered = all.stream()
                .filter(e -> orderNo == null || orderNo.isBlank() || (e.getOrderNo() != null && e.getOrderNo().contains(orderNo.trim())))
                .filter(e -> spotName == null || spotName.isBlank() || (e.getSpotName() != null && e.getSpotName().contains(spotName.trim())))
                .filter(e -> rating == null || (e.getScore() != null && e.getScore().equals(rating)))
                .filter(e -> {
                    if (startDate == null || startDate.isBlank() || e.getCreateTime() == null) return true;
                    return !e.getCreateTime().before(parseDate(startDate));
                })
                .filter(e -> {
                    if (endDate == null || endDate.isBlank() || e.getCreateTime() == null) return true;
                    java.util.Calendar cal = java.util.Calendar.getInstance();
                    cal.setTime(parseDate(endDate));
                    cal.add(java.util.Calendar.DAY_OF_MONTH, 1);
                    return e.getCreateTime().before(cal.getTime());
                })
                // 按评价时间倒序
                .sorted(Comparator.comparing(Evaluation::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        int total = filtered.size();
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(100, size));
        int from = Math.min((safePage - 1) * safeSize, total);
        int to = Math.min(from + safeSize, total);
        List<Evaluation> list = filtered.subList(from, to);

        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", total);
        return data;
    }

    @Override
    @Transactional
    public Evaluation submit(Evaluation evaluation, Long userId) {
        if (evaluation.getOrderId() == null) {
            throw new RuntimeException("请选择要评价的订单");
        }
        Order order = orderRepository.findById(evaluation.getOrderId()).orElse(null);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (userId != null && order.getUserId() != null && !order.getUserId().equals(userId)) {
            throw new RuntimeException("无权评价他人的订单");
        }
        if (order.getStatus() == null || order.getStatus() != 2) {
            throw new RuntimeException("仅已完成（已核销）的订单可评价");
        }
        if (evaluationRepository.findByOrderId(order.getId()).isPresent()) {
            throw new RuntimeException("该订单已评价，请使用“更新评价”");
        }
        if (evaluation.getContent() == null || evaluation.getContent().trim().isEmpty()) {
            throw new RuntimeException("评价内容不能为空");
        }

        evaluation.setUserId(order.getUserId() != null ? order.getUserId() : userId);
        // 评分可为空（即未评分），但不允许超出 0-5
        if (evaluation.getScore() != null && (evaluation.getScore() < 0 || evaluation.getScore() > 5)) {
            throw new RuntimeException("评分范围为 0-5 星");
        }
        evaluation.setSentimentScore(BigDecimal.valueOf(analyzeSentiment(evaluation.getContent())));
        Evaluation saved = evaluationRepository.save(evaluation);
        fillOrderInfo(Collections.singletonList(saved));
        return saved;
    }

    @Override
    @Transactional
    public Evaluation update(Evaluation evaluation) {
        Evaluation existing;
        if (evaluation.getId() != null) {
            existing = evaluationRepository.findById(evaluation.getId()).orElse(null);
        } else {
            existing = evaluation.getOrderId() != null
                    ? evaluationRepository.findByOrderId(evaluation.getOrderId()).orElse(null)
                    : null;
        }
        if (existing == null) {
            throw new RuntimeException("评价不存在");
        }
        if (evaluation.getContent() != null && !evaluation.getContent().trim().isEmpty()) {
            existing.setContent(evaluation.getContent().trim());
        }
        if (evaluation.getScore() != null) {
            if (evaluation.getScore() < 0 || evaluation.getScore() > 5) {
                throw new RuntimeException("评分范围为 0-5 星");
            }
            existing.setScore(evaluation.getScore());
        }
        existing.setSentimentScore(BigDecimal.valueOf(analyzeSentiment(existing.getContent())));
        Evaluation saved = evaluationRepository.save(existing);
        fillOrderInfo(Collections.singletonList(saved));
        return saved;
    }

    @Override
    public Evaluation getByOrderId(Long orderId) {
        if (orderId == null) {
            return null;
        }
        Evaluation evaluation = evaluationRepository.findByOrderId(orderId).orElse(null);
        if (evaluation != null) {
            fillOrderInfo(Collections.singletonList(evaluation));
        }
        return evaluation;
    }

    @Override
    public void delete(Long id) {
        evaluationRepository.deleteById(id);
    }

    // ====== 填充订单号 / 景点名 ======
    private void fillOrderInfo(List<Evaluation> evaluations) {
        if (evaluations == null || evaluations.isEmpty()) return;
        List<Long> orderIds = evaluations.stream()
                .map(Evaluation::getOrderId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Order> orderMap = orderIds.isEmpty() ? Collections.emptyMap()
                : orderRepository.findAllById(orderIds).stream()
                        .collect(Collectors.toMap(Order::getId, o -> o, (a, b) -> a));
        List<Long> policyIds = orderMap.values().stream()
                .map(Order::getPolicyId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, TicketPolicy> policyMap = policyIds.isEmpty() ? Collections.emptyMap()
                : ticketPolicyRepository.findAllById(policyIds).stream()
                        .collect(Collectors.toMap(TicketPolicy::getId, p -> p, (a, b) -> a));
        List<Long> spotIds = policyMap.values().stream()
                .map(TicketPolicy::getSpotId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, ScenicSpot> spotMap = spotIds.isEmpty() ? Collections.emptyMap()
                : scenicSpotRepository.findAllById(spotIds).stream()
                        .collect(Collectors.toMap(ScenicSpot::getId, s -> s, (a, b) -> a));

        for (Evaluation evaluation : evaluations) {
            Order order = orderMap.get(evaluation.getOrderId());
            if (order != null) {
                evaluation.setOrderNo(order.getOrderNo());
                TicketPolicy policy = policyMap.get(order.getPolicyId());
                if (policy != null) {
                    ScenicSpot spot = spotMap.get(policy.getSpotId());
                    if (spot != null) {
                        evaluation.setSpotName(spot.getName());
                    }
                }
            }
        }
    }

    // ====== 简单词典匹配情感分析 ======
    private double analyzeSentiment(String content) {
        if (content == null || content.isEmpty()) return 0;
        String[] positive = {"好", "赞", "美", "满意", "推荐", "值得", "喜欢", "棒", "优秀", "温馨", "不错"};
        String[] negative = {"差", "贵", "乱", "脏", "坑", "失望", "不好", "垃圾", "差评", "麻烦", "等待久"};
        int score = 0;
        for (String word : positive) {
            if (content.contains(word)) score++;
        }
        for (String word : negative) {
            if (content.contains(word)) score--;
        }
        return Math.max(-1, Math.min(1, score / 5.0));
    }

    private Date parseDate(String dateStr) {
        try {
            return java.sql.Date.valueOf(dateStr.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
