package com.scenic.controller;

import com.scenic.service.SandboxAccountService;
import com.scenic.util.JwtUtil;
import com.scenic.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Alipay sandbox account mirror (local mock): merchant in admin personal center, buyer in tourist personal center */
@RestController
@RequestMapping("/api/pay/sandbox")
public class SandboxController {

    private static final String ROLE_MERCHANT = "merchant";
    private static final String ROLE_BUYER = "buyer";

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

    /** Query both sandbox accounts (merchant + buyer, any logged-in user) */
    @GetMapping("/accounts")
    public Result accounts() {
        try {
            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("merchant", sandboxAccountService == null ? null : sandboxAccountService.getMerchantAccount());
            data.put("buyer", sandboxAccountService == null ? null : sandboxAccountService.getBuyerAccount());
            return Result.success(data);
        } catch (Exception e) {
            return Result.error("查询失败：" + e.getMessage());
        }
    }

    /** Recharge sandbox account balance (merchant admin-only / buyer any logged-in) */
    @PostMapping("/recharge")
    public Result recharge(@RequestBody(required = false) Map<String, Object> body,
                           @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return changeBalance(body, authHeader, true);
    }

    /** Withdraw sandbox account balance (merchant admin-only / buyer any logged-in) */
    @PostMapping("/withdraw")
    public Result withdraw(@RequestBody(required = false) Map<String, Object> body,
                           @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return changeBalance(body, authHeader, false);
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

    private Result changeBalance(Map<String, Object> body, String authHeader, boolean recharge) {
        try {
            if (sandboxAccountService == null) return Result.error("沙箱服务不可用");
            String role = body == null ? null : String.valueOf(body.get("role"));
            if (!ROLE_MERCHANT.equals(role) && !ROLE_BUYER.equals(role)) return Result.error("账户类型不正确");
            if (ROLE_MERCHANT.equals(role) && !isAdmin(authHeader)) return Result.error("仅管理员可操作");
            Object amountObj = body == null ? null : body.get("amount");
            if (amountObj == null) return Result.error("金额不能为空");
            BigDecimal amount = new BigDecimal(String.valueOf(amountObj));
            Object acc = recharge
                    ? sandboxAccountService.recharge(role, amount)
                    : sandboxAccountService.withdraw(role, amount);
            return Result.success(acc);
        } catch (Exception e) {
            return Result.error("操作失败：" + e.getMessage());
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
