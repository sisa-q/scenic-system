package com.scenic.service;

import com.scenic.entity.Order;
import com.scenic.vo.PayResult;

import java.util.Map;

public interface PayService {
    PayResult createPayment(Long orderId, Long operatorId, String role);
    String handleNotify(Map<String, String> params);
    /** 支付宝同步跳转（return_url）兜底确认：验签 + 主动查询交易状态 */
    PayResult handleReturn(Map<String, String> params);
    PayResult mockConfirm(Long orderId, Long operatorId, String role);
    /** 前端刷新支付状态兜底：对待支付订单主动查询支付宝并确认（页面按模拟支付逻辑确认订单） */
    PayResult refreshOrderPaymentStatus(Long orderId, Long operatorId, String role);
    void refund(Order order);
}
