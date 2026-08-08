package com.scenic.service;

import com.scenic.entity.ScenicSpot;
import com.scenic.entity.TicketPolicy;
import com.scenic.entity.TimeSlot;
import com.scenic.repository.ScenicSpotRepository;
import com.scenic.repository.TicketPolicyRepository;
import com.scenic.repository.TimeSlotRepository;
import com.scenic.service.impl.TicketPolicyServiceImpl;
import com.scenic.util.RedisCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 票种服务单元测试：库存约束
 *  约束1：景点承载量 >= 该景点所有票种总库存之和
 *  约束2：票种总库存 >= 该票种未来时段库存之和
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("票种服务：库存约束")
class TicketPolicyServiceImplTest {

    @Mock private TicketPolicyRepository ticketPolicyRepository;
    @Mock private ScenicSpotRepository scenicSpotRepository;
    @Mock private TimeSlotRepository timeSlotRepository;
    @Mock private RedisCache redisCache;

    @InjectMocks private TicketPolicyServiceImpl policyService;

    private ScenicSpot spot;

    @BeforeEach
    void setUp() {
        spot = new ScenicSpot();
        spot.setId(1L);
        spot.setCapacity(100);
    }

    @Test
    @DisplayName("新增票种：超过景点承载量 -> 拒绝")
    void add_rejects_when_capacity_exceeded() {
        TicketPolicy other = new TicketPolicy();
        other.setId(2L);
        other.setTotalQuota(80);

        TicketPolicy newPolicy = new TicketPolicy();
        newPolicy.setSpotId(1L);
        newPolicy.setTotalQuota(30);

        when(scenicSpotRepository.findById(1L)).thenReturn(Optional.of(spot));
        when(ticketPolicyRepository.findBySpotId(1L)).thenReturn(List.of(other));

        assertThatThrownBy(() -> policyService.add(newPolicy))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("承载量");
    }

    @Test
    @DisplayName("新增票种：在承载量内 -> 成功")
    void add_success() {
        TicketPolicy other = new TicketPolicy();
        other.setId(2L);
        other.setTotalQuota(30);

        TicketPolicy newPolicy = new TicketPolicy();
        newPolicy.setSpotId(1L);
        newPolicy.setTotalQuota(50);

        when(scenicSpotRepository.findById(1L)).thenReturn(Optional.of(spot));
        when(ticketPolicyRepository.findBySpotId(1L)).thenReturn(List.of(other));
        when(ticketPolicyRepository.save(any(TicketPolicy.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketPolicy saved = policyService.add(newPolicy);

        assertThat(saved.getTotalQuota()).isEqualTo(50);
    }

    @Test
    @DisplayName("修改票种：总库存小于未来时段库存之和 -> 拒绝")
    void update_rejects_when_totalQuota_less_than_slot_sum() {
        TicketPolicy existing = new TicketPolicy();
        existing.setId(5L);
        existing.setSpotId(1L);
        existing.setTotalQuota(5000);

        TicketPolicy update = new TicketPolicy();
        update.setId(5L);
        update.setSpotId(1L);
        update.setName("成人票");
        update.setPrice(new BigDecimal("60.00"));
        update.setTotalQuota(100);

        TimeSlot s1 = futureSlot(150);
        TimeSlot s2 = futureSlot(150);

        when(ticketPolicyRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(scenicSpotRepository.findById(1L)).thenReturn(Optional.of(spot));
        when(ticketPolicyRepository.findBySpotId(1L)).thenReturn(List.of(existing));
        when(timeSlotRepository.findByPolicyId(5L)).thenReturn(List.of(s1, s2));

        assertThatThrownBy(() -> policyService.update(update))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("不能小于该票种未来时段库存之和");
    }

    @Test
    @DisplayName("修改票种：满足约束 -> 成功")
    void update_success() {
        spot.setCapacity(10000); // 承载量需 >= 票种总库存 5000，避免被约束1拦截
        TicketPolicy existing = new TicketPolicy();
        existing.setId(5L);
        existing.setSpotId(1L);
        existing.setTotalQuota(5000);

        TicketPolicy update = new TicketPolicy();
        update.setId(5L);
        update.setSpotId(1L);
        update.setName("成人票");
        update.setPrice(new BigDecimal("60.00"));
        update.setTotalQuota(5000);

        TimeSlot s1 = futureSlot(150);
        TimeSlot s2 = futureSlot(150);

        when(ticketPolicyRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(scenicSpotRepository.findById(1L)).thenReturn(Optional.of(spot));
        when(ticketPolicyRepository.findBySpotId(1L)).thenReturn(List.of(existing));
        when(timeSlotRepository.findByPolicyId(5L)).thenReturn(List.of(s1, s2));
        when(ticketPolicyRepository.save(any(TicketPolicy.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketPolicy saved = policyService.update(update);

        assertThat(saved.getTotalQuota()).isEqualTo(5000);
    }

    private TimeSlot futureSlot(int quota) {
        TimeSlot slot = new TimeSlot();
        slot.setQuota(quota);
        slot.setStartTime(LocalDateTime.now().plusDays(1));
        return slot;
    }
}