package com.scenic.service.impl;

import com.scenic.entity.Order;
import com.scenic.entity.ScenicSpot;
import com.scenic.entity.TicketPolicy;
import com.scenic.entity.VerifyRecord;
import com.scenic.repository.OrderRepository;
import com.scenic.repository.ScenicSpotRepository;
import com.scenic.repository.TicketPolicyRepository;
import com.scenic.repository.VerifyRecordRepository;
import com.scenic.service.FlowStatService;
import com.scenic.service.VerifyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 票务核销服务：
 * 工作人员输入核销码（订单号）完成入园核销，
 * 核销后订单状态自动更新、核销记录写入数据库，
 * 并实时驱动客流统计模块更新。
 */
@Service
public class VerifyServiceImpl implements VerifyService {

    @Autowired
    private VerifyRecordRepository verifyRecordRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TicketPolicyRepository ticketPolicyRepository;

    @Autowired
    private ScenicSpotRepository scenicSpotRepository;

    @Autowired
    private FlowStatService flowStatService;

    @Override
    public List<VerifyRecord> listAll() {
        List<VerifyRecord> records = verifyRecordRepository.findAll();
        if (records.isEmpty()) {
            return records;
        }
        // 填充订单号与景点名
        List<Long> orderIds = records.stream()
                .map(VerifyRecord::getOrderId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Order> orderMap = orderIds.isEmpty() ? Map.of()
                : orderRepository.findAllById(orderIds).stream()
                        .collect(Collectors.toMap(Order::getId, o -> o, (a, b) -> a));

        List<Long> policyIds = orderMap.values().stream()
                .map(Order::getPolicyId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, TicketPolicy> policyMap = policyIds.isEmpty() ? Map.of()
                : ticketPolicyRepository.findAllById(policyIds).stream()
                        .collect(Collectors.toMap(TicketPolicy::getId, p -> p, (a, b) -> a));

        List<Long> spotIds = policyMap.values().stream()
                .map(TicketPolicy::getSpotId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, ScenicSpot> spotMap = spotIds.isEmpty() ? Map.of()
                : scenicSpotRepository.findAllById(spotIds).stream()
                        .collect(Collectors.toMap(ScenicSpot::getId, s -> s, (a, b) -> a));

        for (VerifyRecord record : records) {
            Order order = orderMap.get(record.getOrderId());
            if (order != null) {
                record.setOrderNo(order.getOrderNo());
                TicketPolicy policy = policyMap.get(order.getPolicyId());
                if (policy != null) {
                    ScenicSpot spot = spotMap.get(policy.getSpotId());
                    if (spot != null) {
                        record.setSpotName(spot.getName());
                    }
                }
            }
        }
        return records;
    }

    @Override
    @Transactional
    public void verify(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new RuntimeException("核销码不能为空");
        }
        String verifyCode = code.trim();
        Order order = orderRepository.findByOrderNo(verifyCode).orElse(null);
        if (order == null) {
            throw new RuntimeException("核销码不存在，请检查后重试");
        }
        if (order.getStatus() == 2) {
            throw new RuntimeException("该订单已核销，请勿重复操作");
        }
        if (order.getStatus() == 3) {
            throw new RuntimeException("该订单已退款，无法核销");
        }
        if (order.getStatus() != 1) {
            throw new RuntimeException("订单未支付，无法核销");
        }

        // 更新订单状态为“已使用”
        order.setStatus(2);
        order.setUseTime(new Date());
        orderRepository.save(order);

        // 写入核销记录
        VerifyRecord record = new VerifyRecord();
        record.setOrderId(order.getId());
        record.setVerifyCode(verifyCode);
        record.setOperator("admin");
        verifyRecordRepository.save(record);

        // 实时驱动客流统计更新
        flowStatService.recordEntry(order);
    }
}
