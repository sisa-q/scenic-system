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
    /** 手动“确认支付结果”（沙箱模式闭环）：待支付订单查支付宝，已支付则确认（订单所有者/管理员，需登录） */
    @PostMapping("/refresh/{id}")
    public Result refresh(@PathVariable Long id,
                          @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            return Result.success(payService.refreshOrderPaymentStatus(id, parseUserId(authHeader), parseRole(authHeader)));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

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

    // ====== 辅助解析 ======
    private Long parseUserId(String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
            return Long.parseLong(jwtUtil.getUserIdFromToken(authHeader.substring(7)));
        } catch (Exception e) {
            return null;
        }
    }

    private String parseRole(String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) return "user";
            String role = jwtUtil.getRoleFromToken(authHeader.substring(7));
            return role != null ? role : "user";
        } catch (Exception e) {
            return "user";
        }
    }
}
