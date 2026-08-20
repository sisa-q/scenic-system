package com.scenic.service;

import com.scenic.entity.Order;
import com.scenic.vo.PayResult;

import java.util.Map;

public interface PayService {
    PayResult createPayment(Long orderId, Long operatorId, String role);
    /** 指定支付模式：mode=alipay 走支付宝沙箱，mode=mock/null 走模拟支付（两模式独立） */
    PayResult createPayment(Long orderId, Long operatorId, String role, String mode);
    String handleNotify(Map<String, String> params);
    /** 支付宝同步跳转（return_url）兜底确认：验签 + 主动查询交易状态 */
    PayResult handleReturn(Map<String, String> params);
    PayResult mockConfirm(Long orderId, Long operatorId, String role);
    /** 手动“确认支付结果”：对待支付订单查支付宝，已支付则确认（沙箱模式不依赖异步通知的闭环） */
    PayResult refreshOrderPaymentStatus(Long orderId, Long operatorId, String role);
    void refund(Order order);
}
