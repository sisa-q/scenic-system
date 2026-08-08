package com.scenic.service;

import com.scenic.entity.ScenicSpot;
import com.scenic.entity.TicketPolicy;
import com.scenic.repository.ScenicSpotRepository;
import com.scenic.repository.TicketPolicyRepository;
import com.scenic.service.impl.ScenicSpotServiceImpl;
import com.scenic.util.RedisCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 景点服务单元测试：承载量约束
 *  约束1：景点承载量 >= 该景点所有票种总库存之和
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("景点服务：承载量约束")
class ScenicSpotServiceImplTest {

    @Mock private ScenicSpotRepository scenicSpotRepository;
    @Mock private TicketPolicyRepository ticketPolicyRepository;
    @Mock private RedisCache redisCache;

    @InjectMocks private ScenicSpotServiceImpl spotService;

    @Test
    @DisplayName("修改承载量：小于该景点票种总库存之和 -> 拒绝")
    void update_rejects_when_capacity_below_policy_sum() {
        ScenicSpot existing = new ScenicSpot();
        existing.setId(1L);
        existing.setCapacity(100);

        TicketPolicy p1 = new TicketPolicy();
        p1.setTotalQuota(60);
        TicketPolicy p2 = new TicketPolicy();
        p2.setTotalQuota(50);

        ScenicSpot update = new ScenicSpot();
        update.setId(1L);
        update.setCapacity(80);

        when(scenicSpotRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(ticketPolicyRepository.findBySpotId(1L)).thenReturn(List.of(p1, p2));

        assertThatThrownBy(() -> spotService.update(update))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("承载量");
    }

    @Test
    @DisplayName("修改承载量：满足约束 -> 成功")
    void update_success() {
        ScenicSpot existing = new ScenicSpot();
        existing.setId(1L);
        existing.setCapacity(100);

        TicketPolicy p1 = new TicketPolicy();
        p1.setTotalQuota(60);
        TicketPolicy p2 = new TicketPolicy();
        p2.setTotalQuota(50);

        ScenicSpot update = new ScenicSpot();
        update.setId(1L);
        update.setCapacity(120);

        when(scenicSpotRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(ticketPolicyRepository.findBySpotId(1L)).thenReturn(List.of(p1, p2));
        when(scenicSpotRepository.save(any(ScenicSpot.class))).thenAnswer(inv -> inv.getArgument(0));

        ScenicSpot saved = spotService.update(update);

        assertThat(saved.getCapacity()).isEqualTo(120);
    }
}