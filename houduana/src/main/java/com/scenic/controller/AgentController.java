package com.scenic.controller;

import com.scenic.service.AgentService;
import com.scenic.util.JwtUtil;
import com.scenic.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 游客问答 Agent（工具调用） */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    @Autowired(required = false)
    private AgentService agentService;

    @Autowired(required = false)
    private com.scenic.service.VramMonitor vramMonitor;

    @Autowired
    private JwtUtil jwtUtil;

    /** 对话：可调用工具，危险操作返回确认 */
    @PostMapping("/chat")
    public Result chat(@RequestBody(required = false) Map<String, String> body,
                       @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String question = body == null ? null : body.get("question");
        if (question == null || question.trim().isEmpty()) {
            return Result.error("请输入问题");
        }
        try {
            if (agentService == null) return Result.error("AI 服务未启用");
            Long userId = parseUserId(authHeader);
            String sessionId = body == null ? null : body.get("sessionId");
            String role = body == null ? null : body.get("role");
            return Result.success(agentService.chat(question.trim(), userId, sessionId, role));
        } catch (Exception e) {
            return Result.error("AI 服务暂不可用：" + e.getMessage());
        }
    }

    /** 显存资源与四区状态（最终形态：资源约束降级监控） */
    @GetMapping("/resource")
    public Result resource() {
        if (vramMonitor == null) return Result.success(java.util.Map.of("available", false));
        return Result.success(vramMonitor.info());
    }

    /** 确认危险操作并执行 */
    @PostMapping("/confirm")
    public Result confirm(@RequestBody Map<String, Object> body,
                          @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            if (agentService == null) return Result.error("AI 服务未启用");
            Long userId = parseUserId(authHeader);
            if (userId == null) return Result.error("请先登录");
            String question = String.valueOf(body.getOrDefault("question", ""));
            String action = String.valueOf(body.getOrDefault("action", ""));
            String sessionId = String.valueOf(body.getOrDefault("sessionId", ""));
            String role = String.valueOf(body.getOrDefault("role", ""));
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) body.get("params");
            return Result.success(agentService.confirm(question, action, params, userId, sessionId, role));
        } catch (Exception e) {
            return Result.error("AI 服务暂不可用：" + e.getMessage());
        }
    }

    private Long parseUserId(String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
            String token = authHeader.substring(7);
            if (!jwtUtil.validateToken(token)) return null;
            return Long.parseLong(jwtUtil.getUserIdFromToken(token));
        } catch (Exception e) {
            return null;
        }
    }
}
