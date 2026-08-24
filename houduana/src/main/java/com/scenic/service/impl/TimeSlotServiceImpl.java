package com.scenic.service.impl;

import com.scenic.entity.TimeSlot;
import com.scenic.entity.TicketPolicy;
import com.scenic.repository.TimeSlotRepository;
import com.scenic.repository.TicketPolicyRepository;
import com.scenic.service.TimeSlotService;
import com.scenic.util.RedisCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TimeSlotServiceImpl implements TimeSlotService {

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private TicketPolicyRepository ticketPolicyRepository;

    @Autowired
    private RedisCache redisCache;

    @Override
    public List<TimeSlot> listAll() {
        return redisCache.getListOrLoad("slot:list", TimeSlot.class, 120, () -> {
            List<TimeSlot> slots = timeSlotRepository.findAll();
            fillPolicyInfo(slots);
            return slots;
        });
    }

    @Override
    public List<TimeSlot> listByPolicy(Long policyId) {
        return redisCache.getListOrLoad("slot:list:policy:" + policyId, TimeSlot.class, 120, () -> {
            List<TimeSlot> slots = timeSlotRepository.findByPolicyId(policyId);
            fillPolicyInfo(slots);
            return slots;
        });
    }

    @Override
    public List<TimeSlot> listBySpot(Long spotId) {
        return redisCache.getListOrLoad("slot:list:spot:" + spotId, TimeSlot.class, 120, () -> {
            // 1. 根据景点ID查询所有票种
            List<TicketPolicy> policies = ticketPolicyRepository.findBySpotId(spotId);
            if (policies.isEmpty()) {
                return List.of();
            }
            // 2. 提取所有票种ID
            List<Long> policyIds = policies.stream()
                    .map(TicketPolicy::getId)
                    .collect(Collectors.toList());
            // 3. 查询这些票种下的所有时段
            List<TimeSlot> slots = timeSlotRepository.findByPolicyIdIn(policyIds);
            fillPolicyInfo(slots);
            return slots;
        });
    }

    @Override
    public TimeSlot getById(Long id) {
        return redisCache.getOrLoad("slot:detail:" + id, TimeSlot.class, 120, () -> {
            TimeSlot slot = timeSlotRepository.findById(id).orElse(null);
            if (slot != null) {
                fillPolicyInfo(List.of(slot));
            }
            return slot;
        });
    }

    @Override
    public TimeSlot add(TimeSlot slot) {
        validateSlotQuota(slot, null);
        TimeSlot saved = timeSlotRepository.save(slot);
        evictSlotCache(saved);
        return saved;
    }

    @Override
    public TimeSlot update(TimeSlot slot) {
        // 合并更新：保留已预约数（booked）与创建时间，未传字段保留原值
        TimeSlot existing = timeSlotRepository.findById(slot.getId()).orElse(null);
        if (existing == null) {
            throw new RuntimeException("时段不存在");
        }
        if (slot.getPolicyId() == null) slot.setPolicyId(existing.getPolicyId());
        if (slot.getStartTime() == null) slot.setStartTime(existing.getStartTime());
        if (slot.getEndTime() == null) slot.setEndTime(existing.getEndTime());
        if (slot.getQuota() == null) slot.setQuota(existing.getQuota());
        if (slot.getStatus() == null) slot.setStatus(existing.getStatus());
        slot.setBooked(existing.getBooked());
        if (slot.getBooked() == null) {
            slot.setBooked(0);
        }
        slot.setCreateTime(existing.getCreateTime());
        validateSlotQuota(slot, slot.getId());
        TimeSlot saved = timeSlotRepository.save(slot);
        evictSlotCache(saved);
        return saved;
    }

    @Override
    @Transactional
    public void deleteBatch(java.util.List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        for (Long id : ids) {
            delete(id);
        }
    }

    @Override
    public void delete(Long id) {
        TimeSlot existing = timeSlotRepository.findById(id).orElse(null);
        timeSlotRepository.deleteById(id);
        if (existing != null) {
            evictSlotCache(existing);
        }
    }

    // ====== 缓存失效：写操作后删除相关缓存 ======
    private void evictSlotCache(TimeSlot slot) {
        if (slot == null) {
            return;
        }
        Long spotId = null;
        if (slot.getPolicyId() != null) {
            TicketPolicy policy = ticketPolicyRepository.findById(slot.getPolicyId()).orElse(null);
            spotId = policy != null ? policy.getSpotId() : null;
        }
        redisCache.delete(
                "slot:list",
                slot.getPolicyId() != null ? "slot:list:policy:" + slot.getPolicyId() : null,
                spotId != null ? "slot:list:spot:" + spotId : null,
                slot.getId() != null ? "slot:detail:" + slot.getId() : null
        );
    }

    // ====== 库存约束校验 ======
    private void validateSlotQuota(TimeSlot slot, Long selfId) {
        if (slot.getPolicyId() == null) {
            throw new RuntimeException("时段必须关联票种");
        }
        if (slot.getQuota() == null || slot.getQuota() < 0) {
            throw new RuntimeException("时段库存必须大于等于0");
        }
        int booked = slot.getBooked() == null ? 0 : slot.getBooked();
        // 约束3：时段库存 >= 已预约数
        if (slot.getQuota() < booked) {
            throw new RuntimeException("时段库存(" + slot.getQuota() + ") 不能小于已预约数(" + booked + ")");
        }
        TicketPolicy policy = ticketPolicyRepository.findById(slot.getPolicyId()).orElse(null);
        if (policy == null) {
            throw new RuntimeException("关联票种不存在");
        }
        if (policy.getTotalQuota() == null || policy.getTotalQuota() < 0) {
            throw new RuntimeException("票种总库存未正确设置");
        }
        // 约束2：票种总库存 >= 该票种未来时段库存之和（含本时段）
        LocalDateTime now = LocalDateTime.now();
        int others = timeSlotRepository.findByPolicyId(slot.getPolicyId()).stream()
                .filter(s -> selfId == null || !selfId.equals(s.getId()))
                .filter(s -> s.getStartTime() != null && s.getStartTime().isAfter(now))
                .mapToInt(s -> s.getQuota() == null ? 0 : s.getQuota())
                .sum();
        int total = others + slot.getQuota();
        if (total > policy.getTotalQuota()) {
            throw new RuntimeException("票种总库存(" + policy.getTotalQuota() + ") 不足：该票种未来时段库存之和(" + total + ") 超过票种总库存");
        }
    }

    // ========== 私有方法：填充票种信息 ==========
    private void fillPolicyInfo(List<TimeSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return;
        }
        // 收集所有 policyId
        List<Long> policyIds = slots.stream()
                .map(TimeSlot::getPolicyId)
                .distinct()
                .collect(Collectors.toList());

        // 批量查询票种
        List<TicketPolicy> policies = ticketPolicyRepository.findAllById(policyIds);
        Map<Long, TicketPolicy> policyMap = policies.stream()
                .collect(Collectors.toMap(TicketPolicy::getId, p -> p));

        // 填充 transient 字段
        for (TimeSlot slot : slots) {
            TicketPolicy policy = policyMap.get(slot.getPolicyId());
            if (policy != null) {
                slot.setPolicyName(policy.getName());
                slot.setPrice(policy.getPrice());
            } else {
                slot.setPolicyName("已删除票种");
                slot.setPrice(BigDecimal.ZERO);
            }
        }
    }
}