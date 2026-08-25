package com.scenic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scenic.entity.KnowledgeDoc;
import com.scenic.entity.ScenicSpot;
import com.scenic.entity.TicketPolicy;
import com.scenic.repository.KnowledgeDocRepository;
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
import java.util.*;

/** 游客问答 Agent：Ollama(qwen2.5:3b) 推理 + nomic-embed-text 知识库向量检索(RAG) */
@Service
public class AgentService {

    private static final String SYSTEM_PROMPT = "你是智慧景区游客助手，服务于北京故宫智慧景区系统。可以解答故宫景点、开放时间、门票、分时预约、天气、退款、个人中心等问题。";

    @Autowired(required = false)
    private ScenicSpotRepository spotRepository;

    @Autowired(required = false)
    private TicketPolicyRepository policyRepository;

    @Autowired(required = false)
    private WeatherService weatherService;

    @Autowired(required = false)
    private KnowledgeDocRepository docRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${agent.ollama-url:http://localhost:11434}")
    private String ollamaUrl;

    @Value("${agent.model:qwen2.5:3b}")
    private String model;

    @Value("${agent.embed-model:nomic-embed-text}")
    private String embedModel;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public String chat(String question) throws Exception {
        String prompt = SYSTEM_PROMPT + "\n\n" + buildContext(question) + "\n\n" + "用户问题：" + question + "\n\n" + "请优先依据【知识库相关文档】回答，然后参考【系统信息】，简洁准确友好，用中文；信息里没有的请诚实说明，不要编造。";
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

    /** 构建上下文：系统数据 + 实时天气 + 知识库向量检索 top-k */
    private String buildContext(String question) {
        StringBuilder sb = new StringBuilder();
        sb.append("【系统内景点与门票】").append("\n");
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
        // ===== 知识库 RAG：问题向量化 + 文档相似度检索 top3 =====
        try {
            if (docRepository != null) {
                List<KnowledgeDoc> docs = docRepository.findAll();
                if (!docs.isEmpty()) {
                    float[] qv = embed(question);
                    List<Object[]> scored = new ArrayList<>();
                    for (KnowledgeDoc d : docs) {
                        float[] dv = embed(d.getTitle() + "\n" + d.getContent());
                        scored.add(new Object[]{cosine(qv, dv), d});
                    }
                    scored.sort((a, b) -> Double.compare((double) b[0], (double) a[0]));
                    sb.append("\n").append("【知识库相关文档】").append("\n");
                    int top = Math.min(5, scored.size());
                    for (int i = 0; i < top; i++) {
                        KnowledgeDoc d = (KnowledgeDoc) scored.get(i)[1];
                        sb.append("文档：").append(d.getTitle()).append("\n").append(d.getContent()).append("\n");
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return sb.toString();
    }

    private float[] embed(String text) throws Exception {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("model", embedModel);
        req.put("prompt", text);
        String json = objectMapper.writeValueAsString(req);
        HttpRequest r = HttpRequest.newBuilder()
                .uri(URI.create(ollamaUrl + "/api/embeddings"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = http.send(r, HttpResponse.BodyHandlers.ofString());
        JsonNode node = objectMapper.readTree(resp.body());
        JsonNode emb = node.path("embedding");
        float[] arr = new float[emb.size()];
        for (int i = 0; i < emb.size(); i++) arr[i] = (float) emb.get(i).asDouble();
        return arr;
    }

    private double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb) + 1e-9);
    }
}
