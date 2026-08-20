package com.scenic.controller;

import com.scenic.service.SandboxAccountService;
import com.scenic.util.JwtUtil;
import com.scenic.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

/** 支付宝沙箱账户镜像（本地模拟）：管理端对账查看/重置 */
@RestController
@RequestMapping("/api/pay/sandbox")
public class SandboxController {

    @Autowired(required = false)
    private SandboxAccountService sandboxAccountService;

    @Autowired
    private JwtUtil jwtUtil;

    /** 查询沙箱账户余额（商户/买家） */
    @GetMapping("/accounts")
    public Result accounts(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            if (!isAdmin(authHeader)) return Result.error("仅管理员可查看");
            return Result.success(sandboxAccountService == null ? Collections.emptyList() : sandboxAccountService.listAccounts());
        } catch (Exception e) {
            return Result.error("查询失败：" + e.getMessage());
        }
    }

    /** 查询沙箱余额变动流水 */
    @GetMapping("/flows")
    public Result flows(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            if (!isAdmin(authHeader)) return Result.error("仅管理员可查看");
            return Result.success(sandboxAccountService == null ? Collections.emptyList() : sandboxAccountService.listFlows());
        } catch (Exception e) {
            return Result.error("查询失败：" + e.getMessage());
        }
    }

    /** 重置沙箱账户余额为初始值并清空流水（演示/校准用） */
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
