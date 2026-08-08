package com.scenic.service;

import com.scenic.entity.TicketPolicy;
import com.scenic.entity.TimeSlot;
import com.scenic.repository.TicketPolicyRepository;
import com.scenic.repository.TimeSlotRepository;
import com.scenic.service.impl.TimeSlotServiceImpl;
import com.scenic.util.RedisCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 时段服务单元测试：库存约束
 *  约束3：时段库存 >= 已预约
 *  约束2：票种总库存 >= 该票种未来时段库存之和（含本时段）
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("时段服务：库存约束")
class TimeSlotServiceImplTest {

    @Mock private TimeSlotRepository timeSlotRepository;
    @Mock private TicketPolicyRepository ticketPolicyRepository;
    @Mock private RedisCache redisCache;

    @InjectMocks private TimeSlotServiceImpl slotService;

    @Test
    @DisplayName("新增时段：超过票种总库存 -> 拒绝")
    void add_rejects_when_policy_budget_exceeded() {
        TicketPolicy policy = new TicketPolicy();
        policy.setId(10L);
        policy.setTotalQuota(500);

        TimeSlot existing = futureSlot(1L, 400);
        TimeSlot newSlot = futureSlot(9L, 200);

        when(ticketPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));
        when(timeSlotRepository.findByPolicyId(10L)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> slotService.add(newSlot))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("票种总库存");
    }

    @Test
    @DisplayName("新增时段：库存小于已预约 -> 拒绝")
    void add_rejects_quota_less_than_booked() {
        TimeSlot newSlot = futureSlot(9L, 5);
        newSlot.setBooked(10);

        assertThatThrownBy(() -> slotService.add(newSlot))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("不能小于已预约数");
    }

    @Test
    @DisplayName("新增时段：满足约束 -> 成功")
    void add_success() {
        TicketPolicy policy = new TicketPolicy();
        policy.setId(10L);
        policy.setTotalQuota(500);

        TimeSlot existing = futureSlot(1L, 200);
        TimeSlot newSlot = futureSlot(9L, 100);

        when(ticketPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));
        when(timeSlotRepository.findByPolicyId(10L)).thenReturn(List.of(existing));
        when(timeSlotRepository.save(any(TimeSlot.class))).thenAnswer(inv -> inv.getArgument(0));

        TimeSlot saved = slotService.add(newSlot);

        assertThat(saved.getQuota()).isEqualTo(100);
    }

    @Test
    @DisplayName("修改时段：库存不能小于已预约（保留原预约数） -> 拒绝")
    void update_rejects_quota_less_than_booked() {
        TimeSlot existing = new TimeSlot();
        existing.setId(1L);
        existing.setPolicyId(10L);
        existing.setBooked(10);

        TimeSlot update = new TimeSlot();
        update.setId(1L);
        update.setPolicyId(10L);
        update.setQuota(5);

        when(timeSlotRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> slotService.update(update))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("不能小于已预约数");
    }

    @Test
    @DisplayName("修改时段：成功且保留已预约数")
    void update_success_preserves_booked() {
        TimeSlot existing = new TimeSlot();
        existing.setId(1L);
        existing.setBooked(3);

        TicketPolicy policy = new TicketPolicy();
        policy.setId(10L);
        policy.setTotalQuota(500);

        TimeSlot update = new TimeSlot();
        update.setId(1L);
        update.setPolicyId(10L);
        update.setQuota(50);
        update.setStartTime(LocalDateTime.now().plusDays(1));
        update.setEndTime(LocalDateTime.now().plusDays(1).plusHours(4));
        update.setStatus(1);

        when(timeSlotRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(ticketPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));
        when(timeSlotRepository.findByPolicyId(10L)).thenReturn(List.of());
        when(timeSlotRepository.save(any(TimeSlot.class))).thenAnswer(inv -> inv.getArgument(0));

        TimeSlot saved = slotService.update(update);

        assertThat(saved.getQuota()).isEqualTo(50);
        assertThat(saved.getBooked()).isEqualTo(3);
    }

    private TimeSlot futureSlot(Long id, int quota) {
        TimeSlot slot = new TimeSlot();
        slot.setId(id);
        slot.setPolicyId(10L); // 属于被测票种
        slot.setQuota(quota);
        slot.setBooked(0);
        slot.setStartTime(LocalDateTime.now().plusDays(1));
        slot.setEndTime(LocalDateTime.now().plusDays(1).plusHours(4));
        return slot;
    }
}