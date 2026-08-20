package com.scenic.controller;

import com.scenic.entity.Order;
import com.scenic.service.OrderService;
import com.scenic.service.PayService;
import com.scenic.util.JwtUtil;
import com.scenic.vo.PayResult;
import com.scenic.vo.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/** 订单控制器测试 */
@ExtendWith(MockitoExtension.class)
@DisplayName("订单控制器")
class OrderControllerTest {

    @Mock private OrderService orderService;
    @Mock private PayService payService;
    @Mock private JwtUtil jwtUtil;
    @InjectMocks private OrderController orderController;

    @Test
    void list_ok() {
        when(orderService.listOrders(any(), any(), any(), any())).thenReturn(List.of());

        Result r = orderController.list(null, null, null, null, null);

        assertThat(r.getCode()).isEqualTo(200);
    }

    @Test
    void list_page() {
        when(orderService.pageOrders(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(java.util.Map.of("list", List.of(), "total", 0L));

        Result r = orderController.list(null, null, 1, 10, null);

        assertThat(r.getCode()).isEqualTo(200);
    }

    @Test
    void detail_notFound() {
        when(orderService.getById(1L)).thenReturn(null);

        Result r = orderController.detail(1L, null);

        assertThat(r.getCode()).isEqualTo(500);
        assertThat(r.getMsg()).contains("订单不存在");
    }

    @Test
    void detail_forbidden() {
        Order order = new Order();
        order.setUserId(5L);
        when(orderService.getById(1L)).thenReturn(order);

        // 未登录（authHeader=null）查看别人订单 -> 拒绝
        Result r = orderController.detail(1L, null);

        assertThat(r.getCode()).isEqualTo(500);
        assertThat(r.getMsg()).contains("无权查看");
    }

    @Test
    void pay_ok() {
        when(payService.createPayment(1L, null, "user", null)).thenReturn(new PayResult());

        Result r = orderController.pay(1L, null, null);

        assertThat(r.getCode()).isEqualTo(200);
    }

    @Test
    void refund_ok() {
        Result r = orderController.refund(1L, null);

        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getData()).isEqualTo("退款成功");
    }

    @Test
    void batchDelete_ok() {
        when(orderService.batchDelete(List.of(1L, 2L))).thenReturn(2);

        Map<String, List<Long>> params = new HashMap<>();
        params.put("ids", List.of(1L, 2L));

        Result r = orderController.batchDelete(params);

        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getData()).isEqualTo("成功删除 2 条订单");
    }

    @Test
    void batchDelete_emptyIds() {
        Result r = orderController.batchDelete(new HashMap<>());

        assertThat(r.getCode()).isEqualTo(500);
        assertThat(r.getMsg()).contains("请选择要删除的订单");
    }
}