package com.scenic.controller;

import com.scenic.service.SandboxAccountService;
import com.scenic.util.JwtUtil;
import com.scenic.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/** Alipay sandbox account mirror (local mock): merchant in admin personal center, buyer in tourist personal center */
@RestController
@RequestMapping("/api/pay/sandbox")
public class SandboxController {

    @Autowired(required = false)
    private SandboxAccountService sandboxAccountService;

    @Autowired
    private JwtUtil jwtUtil;

    /** Query merchant sandbox account (admin only, shown in admin personal center) */
    @GetMapping("/merchant")
    public Result merchant(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            if (!isAdmin(authHeader)) return Result.error("仅管理员可查看");
            return Result.success(sandboxAccountService == null ? null : sandboxAccountService.getMerchantAccount());
        } catch (Exception e) {
            return Result.error("查询失败：" + e.getMessage());
        }
    }

    /** Query buyer sandbox account (any logged-in user, shown in tourist personal center) */
    @GetMapping("/buyer")
    public Result buyer() {
        try {
            return Result.success(sandboxAccountService == null ? null : sandboxAccountService.getBuyerAccount());
        } catch (Exception e) {
            return Result.error("查询失败：" + e.getMessage());
        }
    }

    /** Reset sandbox balances to initial value (demo calibration) */
    @PostMapping("/reset")
    public Result reset(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            if (!isAdmin(authHeader)) return Result.error("仅管理员可操作");
            if (sandboxAccountService != null) {
                sandboxAccountService.resetBalances();
            }
            return Result.success("沙箱账户余额已重置");
        } catch (Exception e) {
            return Result.error("重置失败：" + e.getMessage());
        }
    }

    private boolean isAdmin(String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) return false;
            String role = jwtUtil.getRoleFromToken(authHeader.substring(7));
            return "admin".equals(role);
        } catch (Exception e) {
            return false;
        }
    }
}
