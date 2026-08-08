package com.scenic.interceptor;

import com.scenic.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 拦截器：
 * 1. 验证 Token 合法性
 * 2. 基于 Token 中的角色实现接口级别的权限校验（管理员 / 普通游客）
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    /** 仅管理员可访问的接口前缀 */
    private static final String[] ADMIN_ONLY_PREFIXES = {
            // 景点维护
            "/api/spot/add", "/api/spot/update", "/api/spot/delete",
            // 票种维护
            "/api/ticket/add", "/api/ticket/update", "/api/ticket/delete",
            // 时段维护
            "/api/ticket/slot/add", "/api/ticket/slot/update", "/api/ticket/slot/delete",
            // 公告发布
            "/api/notice/add", "/api/notice/update", "/api/notice/delete",
            // 批量删除（取消退款申请允许订单本人操作；单笔退款仅管理员，在服务层校验）
            "/api/order/batch-delete",
            // 评价管理（列表与删除仅管理员）
            "/api/evaluation/list", "/api/evaluation/delete",
            // 核销管理
            "/api/verify",
            // 流客统计大屏
            "/api/flow"
    };

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 跨域预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeError(response, 401, "未授权，请先登录");
            return false;
        }
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            writeError(response, 401, "token 无效或已过期");
            return false;
        }

        // 登出黑名单校验（Redis 可用时；token 登出后立即失效）
        if (redisTemplate != null) {
            String jti = jwtUtil.getJtiFromToken(token);
            if (jti != null && Boolean.TRUE.equals(redisTemplate.hasKey("jwt:blacklist:" + jti))) {
                writeError(response, 401, "token 已失效，请重新登录");
                return false;
            }
        }

        // 角色权限校验
        if (isAdminOnly(request)) {
            String role = jwtUtil.getRoleFromToken(token);
            if (!"admin".equals(role)) {
                writeError(response, 403, "无权限访问，仅管理员可操作");
                return false;
            }
        }
        return true;
    }

    private boolean isAdminOnly(HttpServletRequest request) {
        String uri = request.getRequestURI();
        for (String prefix : ADMIN_ONLY_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        // 评价删除接口 /api/evaluation/{id} （DELETE）仅管理员
        if (uri.startsWith("/api/evaluation/") && "DELETE".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return false;
    }

    private void writeError(HttpServletResponse response, int status, String msg) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + status + ",\"msg\":\"" + msg + "\",\"data\":null}");
    }
}
