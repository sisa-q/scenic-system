package com.scenic.controller;

import com.scenic.service.PayService;
import com.scenic.util.JwtUtil;
import com.scenic.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 支付：发起支付 / 支付宝异步回调 / 同步跳转兜底 / 模拟支付确认 */
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

    /** 支付宝同步跳转 return_url 兜底（验签 + 主动查询交易状态确认，异步回调延迟/丢失时兜底） */
    @GetMapping("/return/alipay")
    public Result returnNotify(@RequestParam Map<String, String> params) {
        try {
            return Result.success(payService.handleReturn(params));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
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