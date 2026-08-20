package com.scenic.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scenic.config.PayProperties;
import com.scenic.entity.Order;
import com.scenic.entity.PayTransaction;
import com.scenic.repository.OrderRepository;
import com.scenic.repository.PayTransactionRepository;
import com.scenic.service.impl.PayServiceImpl;
import com.scenic.util.AlipaySigner;
import com.scenic.vo.PayResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 支付服务测试：发起支付 / 回调确认（模拟通道）/ 幂等 / 金额校验 */
@ExtendWith(MockitoExtension.class)
@DisplayName("支付服务")
class PayServiceImplTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderService orderService;
    @Mock private PayTransactionRepository payTransactionRepository;
    @Mock private PayProperties payProperties;
    @Mock private AlipaySigner alipaySigner;
    @Mock private ObjectMapper objectMapper;
    @InjectMocks private PayServiceImpl payService;

    private Order order;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setId(1L);
        order.setUserId(100L);
        order.setOrderNo("NO20260808");
        order.setTotalAmount(new BigDecimal("60.00"));
        order.setStatus(0);
    }

    @Test
    @DisplayName("发起支付：未启用支付宝时返回 mock 类型")
    void createPayment_mock() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        PayResult r = payService.createPayment(1L, 100L, "user");

        assertThat(r.getType()).isEqualTo("mock");
        assertThat(r.getOrderNo()).isEqualTo("NO20260808");
    }

    @Test
    @DisplayName("发起支付：非本人无权支付")
    void createPayment_forbidden() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> payService.createPayment(1L, 999L, "user"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("无权");
    }

    @Test
    @DisplayName("回调：模拟通道成功确认订单")
    void handleNotify_mock_success() {
        when(payProperties.isEnabled()).thenReturn(false);
        when(payTransactionRepository.findByTransactionId("MOCKNO20260808")).thenReturn(Optional.empty());
        when(orderRepository.findByOrderNo("NO20260808")).thenReturn(Optional.of(order));

        Map<String, String> params = new HashMap<>();
        params.put("out_trade_no", "NO20260808");
        params.put("trade_no", "MOCKNO20260808");
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("total_amount", "60.00");

        assertThat(payService.handleNotify(params)).isEqualTo("success");
        verify(orderService).payOrder(1L, 100L, "user");
        verify(payTransactionRepository).save(any(PayTransaction.class));
    }

    @Test
    @DisplayName("回调：同一交易号幂等，不重复确认")
    void handleNotify_idempotent() {
        when(payTransactionRepository.findByTransactionId("MOCKNO20260808")).thenReturn(Optional.of(new PayTransaction()));

        Map<String, String> params = new HashMap<>();
        params.put("out_trade_no", "NO20260808");
        params.put("trade_no", "MOCKNO20260808");
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("total_amount", "60.00");

        assertThat(payService.handleNotify(params)).isEqualTo("success");
        verify(orderService, never()).payOrder(any(), any(), any());
    }

    @Test
    @DisplayName("回调：金额不一致拒绝")
    void handleNotify_amountMismatch() {
        when(payTransactionRepository.findByTransactionId("MOCKNO20260808")).thenReturn(Optional.empty());
        when(orderRepository.findByOrderNo("NO20260808")).thenReturn(Optional.of(order));

        Map<String, String> params = new HashMap<>();
        params.put("out_trade_no", "NO20260808");
        params.put("trade_no", "MOCKNO20260808");
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("total_amount", "1.00");

        assertThat(payService.handleNotify(params)).isEqualTo("failure");
        verify(orderService, never()).payOrder(any(), any(), any());
    }

    @Test
    @DisplayName("模拟支付确认：即使 channel=alipay 也成功（不走支付宝验签通道）")
    void mockConfirm_worksInAlipayChannel() {
        // 模拟支付与 channel 无关：无需 stub isEnabled/channel
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(payTransactionRepository.findByTransactionId("MOCKNO20260808")).thenReturn(Optional.empty());
        when(orderRepository.findByOrderNo("NO20260808")).thenReturn(Optional.of(order));

        PayResult r = payService.mockConfirm(1L, 100L, "user");

        assertThat(r.getType()).isEqualTo("mock");
        verify(orderService).payOrder(1L, 100L, "user");
    }

    @Test
    @DisplayName("兼容：不传 mode 且 channel=alipay 时返回支付宝沙箱链接（旧前端不传 mode 也能用）")
    void createPayment_noMode_fallsBackToAlipay() {
        when(payProperties.isEnabled()).thenReturn(true);
        when(payProperties.getChannel()).thenReturn("alipay");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        PayResult r = payService.createPayment(1L, 100L, "user", null);

        assertThat(r.getType()).isEqualTo("alipay");
    }
}
