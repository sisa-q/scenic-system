package com.scenic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scenic.entity.ScenicSpot;
import com.scenic.entity.TicketPolicy;
import com.scenic.repository.ScenicSpotRepository;
import com.scenic.repository.TicketPolicyRepository;
import com.scenic.vo.WeatherPoint;
import com.scenic.vo.WeatherVO;
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

/** 游客问答 Agent：调用本地 Ollama（qwen2.5:3b），注入故宫知识 + 系统数据 + 实时天气 */
@Service
public class AgentService {

    private static final String SYSTEM_PROMPT = "你是智慧景区游客助手，服务于北京故宫智慧景区系统。可以解答故宫景点、开放时间、门票、分时预约、天气、退款、个人中心等问题。";

    private static final String KNOWLEDGE = "【故宫景区知识】开放时间：旺季（4月-10月）8:30-17:00，淡季（11月-3月）8:30-16:30，周一闭馆（法定节假日除外）。实行实名制分时预约，可提前在官网、微信小程序或本系统购票预约，入园刷身份证。交通：地铁1号线天安门东站或天安门西站。游览建议：沿中轴线游览太和殿、中和殿、保和殿，再逛东西六宫，全程约3-4小时。";

    @Autowired(required = false)
    private ScenicSpotRepository spotRepository;

    @Autowired(required = false)
    private TicketPolicyRepository policyRepository;

    @Autowired(required = false)
    private WeatherService weatherService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${agent.ollama-url:http://localhost:11434}")
    private String ollamaUrl;

    @Value("${agent.model:qwen2.5:3b}")
    private String model;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public String chat(String question) throws Exception {
        String prompt = SYSTEM_PROMPT + "\n\n" + buildContext() + "\n\n" + "用户问题：" + question + "\n\n" + "请基于以上【系统信息】回答游客问题，简洁准确友好，用中文；信息里没有的请诚实说明，不要编造。";
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

    /** 构建上下文：故宫知识 + 系统内景点/票种 + 故宫实时天气 */
    private String buildContext() {
        StringBuilder sb = new StringBuilder(KNOWLEDGE);
        sb.append("\n\n").append("【系统内景点与门票】").append("\n");
        if (spotRepository != null) {
            for (ScenicSpot s : spotRepository.findAll()) {
                sb.append("景点：").append(s.getName()).append("，")
                  .append(s.getDescription() == null ? "" : s.getDescription()).append("\n");
            }
        }
        if (policyRepository != null) {
            for (TicketPolicy p : policyRepository.findAll()) {
                sb.append("票种：").append(p.getName()).append("，").append("价格 ").append(p.getPrice()).append(" 元").append("\n");
            }
        }
        try {
            if (weatherService != null) {
                WeatherPoint pt = new WeatherPoint();
                pt.setName("故宫");
                pt.setLat(39.9163);
                pt.setLng(116.3972);
                WeatherVO w = weatherService.getWeather(pt);
                if (w != null) {
                    sb.append("\n").append("【故宫实时天气】").append(w.getText()).append("，温度 ").append(w.getTemp()).append("℃")
                      .append("，体感 ").append(w.getFeelsLike()).append("，湿度 ").append(w.getHumidity())
                      .append("，风力 ").append(w.getWind())
                      .append("，降雨概率 ").append(w.getRainProb() == null ? "未知" : w.getRainProb() + "%").append("\n");
                }
            }
        } catch (Exception ignored) {
        }
        return sb.toString();
    }
}
