package com.scenic.service;

import com.scenic.entity.Order;
import com.scenic.entity.VerifyRecord;
import com.scenic.repository.OrderRepository;
import com.scenic.repository.ScenicSpotRepository;
import com.scenic.repository.TicketPolicyRepository;
import com.scenic.repository.VerifyRecordRepository;
import com.scenic.service.impl.VerifyServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 票务核销服务测试：核销流程与各种拒绝场景 */
@ExtendWith(MockitoExtension.class)
@DisplayName("核销服务")
class VerifyServiceImplTest {

    @Mock private VerifyRecordRepository verifyRecordRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private TicketPolicyRepository ticketPolicyRepository;
    @Mock private ScenicSpotRepository scenicSpotRepository;
    @Mock private FlowStatService flowStatService;
    @InjectMocks private VerifyServiceImpl verifyService;

    private Order paidOrder() {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo("NO20260101");
        order.setStatus(1); // 已支付
        order.setQuantity(2);
        order.setPolicyId(10L);
        return order;
    }

    @Test
    @DisplayName("核销成功：订单变已使用，写核销记录，驱动客流统计")
    void verify_success() {
        Order order = paidOrder();
        when(orderRepository.findByOrderNo("NO20260101")).thenReturn(Optional.of(order));

        verifyService.verify("NO20260101");

        assertThat(order.getStatus()).isEqualTo(2);   // 已使用
        assertThat(order.getUseTime()).isNotNull();
        verify(verifyRecordRepository).save(any(VerifyRecord.class));
        verify(flowStatService).recordEntry(order);
    }

    @Test
    @DisplayName("核销失败：核销码为空")
    void verify_emptyCode() {
        assertThatThrownBy(() -> verifyService.verify("   "))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("核销码不能为空");
    }

    @Test
    @DisplayName("核销失败：核销码不存在")
    void verify_notFound() {
        when(orderRepository.findByOrderNo("X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> verifyService.verify("X"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("核销码不存在");
    }

    @Test
    @DisplayName("核销失败：已核销不可重复")
    void verify_alreadyVerified() {
        Order order = paidOrder();
        order.setStatus(2);
        when(orderRepository.findByOrderNo("NO20260101")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> verifyService.verify("NO20260101"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("已核销");
    }

    @Test
    @DisplayName("核销失败：已退款不可核销")
    void verify_refunded() {
        Order order = paidOrder();
        order.setStatus(3);
        when(orderRepository.findByOrderNo("NO20260101")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> verifyService.verify("NO20260101"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("已退款");
    }

    @Test
    @DisplayName("核销失败：未支付不可核销")
    void verify_notPaid() {
        Order order = paidOrder();
        order.setStatus(0);
        when(orderRepository.findByOrderNo("NO20260101")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> verifyService.verify("NO20260101"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("未支付");
    }
}