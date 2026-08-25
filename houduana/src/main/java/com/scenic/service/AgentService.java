package com.scenic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** 游客问答 Agent：调用本地 Ollama（qwen2.5:3b） */
@Service
public class AgentService {

    private static final String SYSTEM_PROMPT = "你是智慧景区游客助手，服务于北京故宫智慧景区系统。可以解答故宫景点、开放时间、门票、分时预约、天气、退款、个人中心等问题。回答简洁友好，用中文。不知道的就诚实说不知道。";

    @Value("${agent.ollama-url:http://localhost:11434}")
    private String ollamaUrl;

    @Value("${agent.model:qwen2.5:3b}")
    private String model;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public String chat(String question) throws Exception {
        String prompt = SYSTEM_PROMPT + "\n\u7528\u6237\u95EE\u9898\uFF1A" + question;
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("model", model);
        req.put("prompt", prompt);
        req.put("stream", false);
        Map<String, Object> opts = new LinkedHashMap<>();
        opts.put("temperature", 0.7);
        req.put("options", opts);

        String json = objectMapper.writeValueAsString(req);
        HttpRequest r = HttpRequest.newBuilder()
                .uri(URI.create(ollamaUrl + "/api/generate"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = http.send(r, HttpResponse.BodyHandlers.ofString());
        JsonNode node = objectMapper.readTree(resp.body());
        String answer = node.path("response").asText("");
        if (answer.isEmpty()) {
            String err = node.path("error").asText("");
            throw new RuntimeException(err.isEmpty() ? "no response" : err);
        }
        return answer;
    }
}
