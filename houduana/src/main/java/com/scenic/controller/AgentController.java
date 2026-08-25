package com.scenic.controller;

import com.scenic.service.AgentService;
import com.scenic.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 游客问答 Agent */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    @Autowired(required = false)
    private AgentService agentService;

    @PostMapping("/chat")
    public Result chat(@RequestBody(required = false) Map<String, String> body) {
        String question = body == null ? null : body.get("question");
        if (question == null || question.trim().isEmpty()) {
            return Result.error("请输入问题");
        }
        try {
            if (agentService == null) return Result.error("AI 服务未启用");
            String answer = agentService.chat(question.trim());
            return Result.success(answer);
        } catch (Exception e) {
            return Result.error("AI 服务暂不可用：" + e.getMessage());
        }
    }
}
