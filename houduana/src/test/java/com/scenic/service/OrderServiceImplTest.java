package com.scenic.service;

import com.scenic.entity.Order;
import com.scenic.entity.TicketPolicy;
import com.scenic.entity.TimeSlot;
import com.scenic.repository.OrderRepository;
import com.scenic.repository.ScenicSpotRepository;
import com.scenic.repository.TicketPolicyRepository;
import com.scenic.repository.TimeSlotRepository;
import com.scenic.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单服务单元测试（JUnit 5 + Mockito）
 *
 * 覆盖：
 *  1) 下单：成功 / 余票不足 / 时段关闭 / 票种下架
 *  2) 支付：成功（预约数增加）/ 余票不足
 *  3) 退款：游客申请只进入"申请退款中"，绝不自动退款；
 *           管理员才真正退款并释放预约数；非本人无权限；已退款/已核销不可退
 *  4) 取消退款申请
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("订单服务：下单 / 支付 / 退款")
class OrderServiceImplTest {

    @Mock private OrderRepository orderRepository;
    @Mock private TimeSlotRepository timeSlotRepository;
    @Mock private TicketPolicyRepository ticketPolicyRepository;
    @Mock private ScenicSpotRepository scenicSpotRepository;

    @InjectMocks private OrderServiceImpl orderService;

    private TimeSlot slot;
    private TicketPolicy policy;
    private final Long userId = 100L;

    @BeforeEach
    void setUp() {
        slot = new TimeSlot();
        slot.setId(1L);
        slot.setPolicyId(10L);
        slot.setQuota(100);
        slot.setBooked(0);
        slot.setStatus(1);
        slot.setStartTime(LocalDateTime.now().plusDays(1));
        slot.setEndTime(LocalDateTime.now().plusDays(1).plusHours(4));

        policy = new TicketPolicy();
        policy.setId(10L);
        policy.setSpotId(1L);
        policy.setName("成人票");
        policy.setPrice(new BigDecimal("60.00"));
        policy.setTotalQuota(5000);
        policy.setStatus(1);
    }

    // ==================== 下单 ====================

    @Test
    @DisplayName("下单成功：生成待支付订单，金额=单价×数量")
    void createOrder_success() {
        when(timeSlotRepository.findById(1L)).thenReturn(Optional.of(slot));
        when(ticketPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));

        Order order = new Order();
        order.setSlotId(1L);
        order.setQuantity(2);
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(orderRepository.findById(any())).thenReturn(Optional.of(order));

        Order saved = orderService.createOrder(order, userId);

        assertThat(saved.getStatus()).isZero();
        assertThat(saved.getPolicyId()).isEqualTo(10L);
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo(new BigDecimal("120.00"));
        assertThat(saved.getOrderNo()).isNotBlank();
    }

    @Test
    @DisplayName("下单失败：余票不足")
    void createOrder_insufficientStock() {
        slot.setBooked(98);
        when(timeSlotRepository.findById(1L)).thenReturn(Optional.of(slot));
        when(ticketPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));

        Order order = new Order();
        order.setSlotId(1L);
        order.setQuantity(5);

        assertThatThrownBy(() -> orderService.createOrder(order, userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("余票不足");
    }

    @Test
    @DisplayName("下单失败：时段已关闭")
    void createOrder_slotClosed() {
        slot.setStatus(0);
        when(timeSlotRepository.findById(1L)).thenReturn(Optional.of(slot));

        Order order = new Order();
        order.setSlotId(1L);
        order.setQuantity(1);

        assertThatThrownBy(() -> orderService.createOrder(order, userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("该时段已关闭");
    }

    @Test
    @DisplayName("下单失败：票种已下架")
    void createOrder_policyOff() {
        policy.setStatus(0);
        when(timeSlotRepository.findById(1L)).thenReturn(Optional.of(slot));
        when(ticketPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));

        Order order = new Order();
        order.setSlotId(1L);
        order.setQuantity(1);

        assertThatThrownBy(() -> orderService.createOrder(order, userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("该票种已下架");
    }

    // ==================== 支付 ====================

    @Test
    @DisplayName("支付成功：订单变已支付，时段预约数增加")
    void payOrder_success() {
        Order order = pendingOrder();
        when(orderRepository.findById(anyLong())).thenReturn(Optional.of(order));
        when(timeSlotRepository.findById(1L)).thenReturn(Optional.of(slot));

        orderService.payOrder(1L, userId, "user");

        assertThat(order.getStatus()).isEqualTo(1);
        assertThat(slot.getBooked()).isEqualTo(2);
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("支付失败：该时段余票不足")
    void payOrder_insufficient() {
        slot.setBooked(99);
        Order order = pendingOrder();
        when(orderRepository.findById(anyLong())).thenReturn(Optional.of(order));
        when(timeSlotRepository.findById(1L)).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> orderService.payOrder(1L, userId, "user"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("余票不足");
    }

    // ==================== 退款 ====================

    @Test
    @DisplayName("游客申请退款：只进入申请退款中(5)，绝不自动退款")
    void refund_tourist_apply_only() {
        Order order = paidOrder();
        when(orderRepository.findById(anyLong())).thenReturn(Optional.of(order));

        orderService.applyRefund(1L, userId); // 游客端"申请退款"走 applyRefund

        assertThat(order.getStatus()).isEqualTo(5);
        assertThat(order.getRefundRequestTime()).isNotNull();
        assertThat(order.getRefundTime()).isNull();
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("游客不能替别人申请退款")
    void refund_tourist_notOwner() {
        Order order = paidOrder();
        when(orderRepository.findById(anyLong())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.applyRefund(1L, 200L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("仅订单本人可申请退款");
    }

    @Test
    @DisplayName("非管理员调用退款接口：直接拒绝（只有管理员能真正退款）")
    void refund_admin_only() {
        assertThatThrownBy(() -> orderService.refundOrder(1L, userId, "user"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("仅管理员可执行退款操作");
    }

    @Test
    @DisplayName("管理员退款：真正退款(3)，并释放时段预约数")
    void refund_admin_realRefund() {
        Order order = paidOrder();
        slot.setBooked(5);
        when(orderRepository.findById(anyLong())).thenReturn(Optional.of(order));
        when(timeSlotRepository.findById(1L)).thenReturn(Optional.of(slot));

        orderService.refundOrder(1L, userId, "admin");

        assertThat(order.getStatus()).isEqualTo(3);
        assertThat(order.getRefundTime()).isNotNull();
        assertThat(slot.getBooked()).isEqualTo(3);
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("管理员可审批退款申请中的订单")
    void refund_admin_pendingApplication() {
        Order order = paidOrder();
        order.setStatus(5);
        when(orderRepository.findById(anyLong())).thenReturn(Optional.of(order));

        orderService.refundOrder(1L, userId, "admin");

        assertThat(order.getStatus()).isEqualTo(3);
    }

    @Test
    @DisplayName("已退款订单不能重复退")
    void refund_alreadyRefunded() {
        Order order = paidOrder();
        order.setStatus(3);
        when(orderRepository.findById(anyLong())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.refundOrder(1L, userId, "admin"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("请勿重复操作");
    }

    @Test
    @DisplayName("已核销订单不能退款")
    void refund_usedCannot() {
        Order order = paidOrder();
        order.setStatus(2);
        when(orderRepository.findById(anyLong())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.refundOrder(1L, userId, "admin"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("已核销");
    }

    @Test
    @DisplayName("游客取消退款申请：恢复为已支付")
    void cancelRefund_success() {
        Order order = paidOrder();
        order.setStatus(5);
        order.setRefundRequestTime(new Date());
        when(orderRepository.findById(anyLong())).thenReturn(Optional.of(order));

        orderService.cancelRefund(1L, userId, "user");

        assertThat(order.getStatus()).isEqualTo(1);
        assertThat(order.getRefundRequestTime()).isNull();
    }

    /** 待支付订单（支付测试用） */
    private Order pendingOrder() {
        Order order = new Order();
        order.setId(1L);
        order.setUserId(userId);
        order.setSlotId(1L);
        order.setQuantity(2);
        order.setStatus(0);
        order.setCreateTime(new Date());
        return order;
    }

    /** 已支付订单（退款测试用） */
    private Order paidOrder() {
        Order order = pendingOrder();
        order.setStatus(1);
        return order;
    }
}