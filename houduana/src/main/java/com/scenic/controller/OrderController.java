package com.scenic.controller;

import com.scenic.entity.Order;
import com.scenic.service.OrderService;
import com.scenic.service.PayService;
import com.scenic.util.JwtUtil;
import com.scenic.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private PayService payService;

    @Autowired
    private JwtUtil jwtUtil;

    // ==================== ????????????? ====================
    @PostMapping("/clear")
    public Result clear(@RequestHeader("Authorization") String authHeader) {
        try {
            orderService.clearOrderData();
            return Result.success("订单数据已清空");
        } catch (Exception e) {
            return Result.error("清空失败：" + e.getMessage());
        }
    }

    @GetMapping("/list")
    public Result list(@RequestParam(required = false) Integer status,
                       @RequestParam(required = false) String key,
                       @RequestParam(required = false) Integer page,
                       @RequestParam(required = false) Integer size,
                       @RequestHeader(value = "Authorization", required = false) String authHeader) {

        Long userId = null;
        String role = "user";

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7);
                userId = Long.parseLong(jwtUtil.getUserIdFromToken(token));
                String tokenRole = jwtUtil.getRoleFromToken(token);
                if (tokenRole != null) {
                    role = tokenRole;
                }
            } catch (Exception e) {
                System.err.println("Token 解析失败: " + e.getMessage());
            }
        }

        // 传了 page/size 时走数据库分页；否则保持旧的全局列表返回给前端
        if (page != null && size != null) {
            return Result.success(orderService.pageOrders(status, key, userId, role, page, size));
        }
        List<Order> orders = orderService.listOrders(status, key, userId, role);
        return Result.success(orders);
    }

    @GetMapping("/detail/{id}")
    public Result detail(@PathVariable Long id,
                         @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Order order = orderService.getById(id);
        if (order == null) {
            return Result.error("订单不存在");
        }
        // 权限校验：非管理员只能查看自己的订单
        String role = parseRole(authHeader);
        Long userId = parseUserId(authHeader);
        if (!"admin".equals(role) && (userId == null || order.getUserId() == null || !userId.equals(order.getUserId()))) {
            return Result.error("无权查看该订单");
        }
        return Result.success(order);
    }

    @PostMapping("/create")
    public Result create(@RequestBody Order order,
                         @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            Long userId = Long.parseLong(jwtUtil.getUserIdFromToken(token));
            Order created = orderService.createOrder(order, userId);
            return Result.success(created);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("下单失败：" + e.getMessage());
        }
    }

    @PostMapping("/pay/{id}")
    public Result pay(@PathVariable Long id,
                      @RequestParam(required = false) String mode,
                      @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            // mode=alipay 走支付宝沙箱，mode=mock/null 走模拟支付（两个模式独立）
            return Result.success(payService.createPayment(id, parseUserId(authHeader), parseRole(authHeader), mode));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 游客申请退款：任何角色都只是登记“退款申请中”，由管理员在“退款”中处理 */
    @PostMapping("/refund-apply/{id}")
    public Result applyRefund(@PathVariable Long id,
                              @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            orderService.applyRefund(id, parseUserId(authHeader));
            return Result.success("退款申请已提交，等待管理员处理");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 管理员退款：仅管理员角色可执行真正退款 */
    @PostMapping("/refund/{id}")
    public Result refund(@PathVariable Long id,
                         @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            orderService.refundOrder(id, parseUserId(authHeader), parseRole(authHeader));
            return Result.success("退款成功");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 取消退款申请（游客或管理员） */
    @PostMapping("/cancel-refund/{id}")
    public Result cancelRefund(@PathVariable Long id,
                               @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            orderService.cancelRefund(id, parseUserId(authHeader), parseRole(authHeader));
            return Result.success("已取消退款申请");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/batch-delete")
    public Result batchDelete(@RequestBody Map<String, List<Long>> params) {
        List<Long> ids = params.get("ids");
        if (ids == null || ids.isEmpty()) {
            return Result.error("请选择要删除的订单");
        }
        try {
            int deletedCount = orderService.batchDelete(ids);
            return Result.success("成功删除 " + deletedCount + " 条订单");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("删除失败：" + e.getMessage());
        }
    }

    @PutMapping("/hide")
    public Result hideOrders(@RequestBody Map<String, List<Long>> params,
                             @RequestHeader("Authorization") String authHeader) {
        List<Long> ids = params.get("ids");
        if (ids == null || ids.isEmpty()) {
            return Result.error("请选择要隐藏的订单");
        }
        try {
            String token = authHeader.substring(7);
            Long userId = Long.parseLong(jwtUtil.getUserIdFromToken(token));
            int count = orderService.hideOrders(ids, userId);
            return Result.success("已隐藏 " + count + " 条订单");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("隐藏失败：" + e.getMessage());
        }
    }

    // ====== 辅助解析 ======

    private Long parseUserId(String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return null;
            }
            String token = authHeader.substring(7);
            return Long.parseLong(jwtUtil.getUserIdFromToken(token));
        } catch (Exception e) {
            return null;
        }
    }

    private String parseRole(String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return "user";
            }
            String token = authHeader.substring(7);
            String role = jwtUtil.getRoleFromToken(token);
            return role != null ? role : "user";
        } catch (Exception e) {
            return "user";
        }
    }
}
