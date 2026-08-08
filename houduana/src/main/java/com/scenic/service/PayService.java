package com.scenic.service;

import com.scenic.entity.Order;
import com.scenic.vo.PayResult;

import java.util.Map;

public interface PayService {
    PayResult createPayment(Long orderId, Long operatorId, String role);
    String handleNotify(Map<String, String> params);
    PayResult mockConfirm(Long orderId, Long operatorId, String role);
    void refund(Order order);
}
