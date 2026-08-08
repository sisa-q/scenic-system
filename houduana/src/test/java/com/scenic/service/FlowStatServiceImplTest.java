package com.scenic.service;

import com.scenic.entity.FlowStat;
import com.scenic.entity.Order;
import com.scenic.entity.TicketPolicy;
import com.scenic.repository.FlowStatRepository;
import com.scenic.repository.OrderRepository;
import com.scenic.repository.ScenicSpotRepository;
import com.scenic.repository.TicketPolicyRepository;
import com.scenic.repository.TimeSlotRepository;
import com.scenic.service.impl.FlowStatServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 客流统计服务测试：统计汇总 / 实时 / 核销记客流 */
@ExtendWith(MockitoExtension.class)
@DisplayName("客流统计服务")
class FlowStatServiceImplTest {

    @Mock private FlowStatRepository flowStatRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private TimeSlotRepository timeSlotRepository;
    @Mock private TicketPolicyRepository ticketPolicyRepository;
    @Mock private ScenicSpotRepository scenicSpotRepository;
    @InjectMocks private FlowStatServiceImpl flowStatService;

    @Test
    @DisplayName("统计汇总：返回所有指标键")
    void getStats_returnsAllKeys() {
        // 仓库默认返回空列表/0，方法应正常走完全部分支
        Map<String, Object> data = flowStatService.getStats();

        assertThat(data).containsKeys(
                "todayEntered", "currentVisitors", "todayOrders",
                "totalVisitors", "dates", "trend", "hourlyDistribution");
        assertThat((Integer) data.get("todayOrders")).isZero();
    }

    @Test
    @DisplayName("实时客流：返回在园/今日入园/更新时间")
    void getRealtime_returnsKeys() {
        Map<String, Object> data = flowStatService.getRealtime();

        assertThat(data).containsKeys("currentVisitors", "todayEntered", "updateTime");
    }

    @Test
    @DisplayName("核销记客流：写入 flow_stat")
    void recordEntry_writesFlowStat() {
        Order order = new Order();
        order.setPolicyId(10L);
        order.setQuantity(2);

        TicketPolicy policy = new TicketPolicy();
        policy.setSpotId(1L);

        when(ticketPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));
        when(flowStatRepository.findBySpotIdAndStatTime(eq(1L), any(Date.class))).thenReturn(Optional.empty());

        flowStatService.recordEntry(order);

        verify(flowStatRepository).save(any(FlowStat.class));
    }
}