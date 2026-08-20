package com.scenic.controller;

import com.scenic.service.PayService;
import com.scenic.util.JwtUtil;
import com.scenic.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 支付相关：发起支付 / 支付宝异步回调 / 同步跳转兜底 / 主动查单确认 / 模拟支付确认 */
@RestController
@RequestMapping("/api/pay")
public class PayController {

    @Autowired
    private PayService payService;

    @Autowired
    private JwtUtil jwtUtil;

    /** 支付宝异步通知（notify_url）：验签后确认订单 */
    @PostMapping("/notify/alipay")
    public String notify(@RequestParam Map<String, String> params) {
        return payService.handleNotify(params);
    }

    /** 支付宝同步跳转 return_url 兜底：验签 + 主动查询交易状态确认（异步通知延迟/丢失时兜底） */
    @GetMapping("/return/alipay")
    public Result returnNotify(@RequestParam Map<String, String> params) {
        try {
            return Result.success(payService.handleReturn(params));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 前端“刷新支付状态”兜底：对待支付订单主动查询支付宝并确认（订单所有者/管理员，需登录） */
    @PostMapping("/refresh/{id}")
    public Result refresh(@PathVariable Long id,
                          @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            return Result.success(payService.refreshOrderPaymentStatus(id, parseUserId(authHeader), parseRole(authHeader)));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 模拟支付确认：仅开发/演示用（本地联调，非真实支付宝） */
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
