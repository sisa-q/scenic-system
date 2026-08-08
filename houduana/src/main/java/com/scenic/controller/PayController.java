package com.scenic.controller;

import com.scenic.service.PayService;
import com.scenic.util.JwtUtil;
import com.scenic.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 支付：发起支付 / 支付宝异步回调 / 模拟支付确认 */
@RestController
@RequestMapping("/api/pay")
public class PayController {

    @Autowired
    private PayService payService;

    @Autowired
    private JwtUtil jwtUtil;

    /** 支付宝异步回调（公开接口，验签在服务层） */
    @PostMapping("/notify/alipay")
    public String notify(@RequestParam Map<String, String> params) {
        return payService.handleNotify(params);
    }

    /** 模拟支付确认（开发/演示用） */
    @PostMapping("/mock/confirm/{id}")
    public Result mockConfirm(@PathVariable Long id,
                              @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            Long userId = null;
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                userId = Long.parseLong(jwtUtil.getUserIdFromToken(authHeader.substring(7)));
            }
            return Result.success(payService.mockConfirm(id, userId, "user"));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
