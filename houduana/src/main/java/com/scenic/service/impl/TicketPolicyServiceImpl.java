package com.scenic.service.impl;

import com.scenic.entity.ScenicSpot;
import com.scenic.entity.TicketPolicy;
import com.scenic.repository.ScenicSpotRepository;
import com.scenic.repository.TicketPolicyRepository;
import com.scenic.repository.TimeSlotRepository;
import com.scenic.service.TicketPolicyService;
import com.scenic.util.RedisCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TicketPolicyServiceImpl implements TicketPolicyService {

    @Autowired
    private TicketPolicyRepository ticketPolicyRepository;

    @Autowired
    private ScenicSpotRepository scenicSpotRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private RedisCache redisCache;

    @Override
    public List<TicketPolicy> listAll() {
        return redisCache.getListOrLoad("ticket:list", TicketPolicy.class, 300, () -> {
            List<TicketPolicy> policies = ticketPolicyRepository.findAll();
            fillSpotName(policies);
            return policies;
        });
    }

    @Override
    public List<TicketPolicy> listBySpot(Long spotId) {
        return redisCache.getListOrLoad("ticket:list:spot:" + spotId, TicketPolicy.class, 300, () -> {
            List<TicketPolicy> policies = ticketPolicyRepository.findBySpotId(spotId);
            fillSpotName(policies);
            return policies;
        });
    }

    @Override
    public TicketPolicy getById(Long id) {
        return redisCache.getOrLoad("ticket:detail:" + id, TicketPolicy.class, 300, () -> {
            TicketPolicy policy = ticketPolicyRepository.findById(id).orElse(null);
            if (policy != null) {
                fillSpotName(List.of(policy));
            }
            return policy;
        });
    }

    @Override
    public TicketPolicy add(TicketPolicy policy) {
        validatePolicyQuota(policy, null);
        TicketPolicy saved = ticketPolicyRepository.save(policy);
        // 写操作：删除相关缓存，保证下次读到最新数据
        redisCache.delete("ticket:list", "ticket:list:spot:" + saved.getSpotId(), "ticket:detail:" + saved.getId());
        return saved;
    }

    @Override
    public TicketPolicy update(TicketPolicy policy) {
        TicketPolicy existing = ticketPolicyRepository.findById(policy.getId()).orElse(null);
        if (existing == null) {
            throw new RuntimeException("票种不存在");
        }
        // 合并更新：未传字段保留库内原值，避免覆盖为空
        if (policy.getSpotId() == null) policy.setSpotId(existing.getSpotId());
        if (policy.getName() == null || policy.getName().trim().isEmpty()) policy.setName(existing.getName());
        if (policy.getPrice() == null) policy.setPrice(existing.getPrice());
        if (policy.getTotalQuota() == null) policy.setTotalQuota(existing.getTotalQuota());
        if (policy.getRefundRule() == null) policy.setRefundRule(existing.getRefundRule());
        if (policy.getStatus() == null) policy.setStatus(existing.getStatus());
        policy.setCreateTime(existing.getCreateTime());
        validatePolicyQuota(policy, policy.getId());
        TicketPolicy saved = ticketPolicyRepository.save(policy);
        redisCache.delete("ticket:list", "ticket:list:spot:" + saved.getSpotId(), "ticket:detail:" + saved.getId());
        return saved;
    }

    @Override
    public void delete(Long id) {
        TicketPolicy policy = ticketPolicyRepository.findById(id).orElse(null);
        ticketPolicyRepository.deleteById(id);
        if (policy != null) {
            redisCache.delete("ticket:list", "ticket:list:spot:" + policy.getSpotId(), "ticket:detail:" + id);
        }
    }

    // ====== 库存约束校验 ======
    private void validatePolicyQuota(TicketPolicy policy, Long selfId) {
        if (policy.getSpotId() == null) {
            throw new RuntimeException("票种必须关联景点");
        }
        if (policy.getTotalQuota() == null || policy.getTotalQuota() < 0) {
            throw new RuntimeException("票种总库存必须大于等于0");
        }
        ScenicSpot spot = scenicSpotRepository.findById(policy.getSpotId()).orElse(null);
        if (spot == null) {
            throw new RuntimeException("关联景点不存在");
        }
        // 约束1：景点承载量 >= 该景点所有票种总库存之和（含本票种）
        int others = ticketPolicyRepository.findBySpotId(policy.getSpotId()).stream()
                .filter(p -> selfId == null || !selfId.equals(p.getId()))
                .mapToInt(p -> p.getTotalQuota() == null ? 0 : p.getTotalQuota())
                .sum();
        int total = others + policy.getTotalQuota();
        if (spot.getCapacity() != null && total > spot.getCapacity()) {
            throw new RuntimeException("景点承载量不足：该景点票种总库存之和(" + total + ") 不能超过景点承载量(" + spot.getCapacity() + ")");
        }
        // 约束2：票种总库存 >= 该票种未来时段库存之和
        if (selfId != null) {
            int slotSum = timeSlotRepository.findByPolicyId(selfId).stream()
                    .filter(s -> s.getStartTime() != null && s.getStartTime().isAfter(java.time.LocalDateTime.now()))
                    .mapToInt(s -> s.getQuota() == null ? 0 : s.getQuota())
                    .sum();
            if (slotSum > policy.getTotalQuota()) {
                throw new RuntimeException("票种总库存(" + policy.getTotalQuota() + ") 不能小于该票种未来时段库存之和(" + slotSum + ")");
            }
        }
    }

    // ====== 私有方法：填充景点名称 ======
    private void fillSpotName(List<TicketPolicy> policies) {
        if (policies == null || policies.isEmpty()) {
            return;
        }

        // 收集所有 spotId
        List<Long> spotIds = policies.stream()
                .map(TicketPolicy::getSpotId)
                .distinct()
                .collect(Collectors.toList());

        // 批量查询景点
        List<ScenicSpot> spots = scenicSpotRepository.findAllById(spotIds);
        Map<Long, String> spotNameMap = spots.stream()
                .collect(Collectors.toMap(ScenicSpot::getId, ScenicSpot::getName));

        // 填充 spotName
        for (TicketPolicy policy : policies) {
            String name = spotNameMap.get(policy.getSpotId());
            policy.setSpotName(name != null ? name : "未知景点");
        }
    }
}