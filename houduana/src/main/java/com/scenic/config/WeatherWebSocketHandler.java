package com.scenic.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scenic.service.WeatherService;
import com.scenic.vo.WeatherPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 天气推送 WebSocket 处理器
 * 游客端首页连接 /ws/weather 并订阅景点坐标，后端每 30 分钟检测降雨/极端天气并推送提醒
 */
@Component
public class WeatherWebSocketHandler extends TextWebSocketHandler {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final Map<WebSocketSession, List<WeatherPoint>> subscriptions = new ConcurrentHashMap<>();

    @Autowired
    private WeatherService weatherService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        subscriptions.remove(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode root = objectMapper.readTree(message.getPayload());
            if (root != null && "subscribe".equals(root.path("type").asText()) && root.has("points")) {
                List<WeatherPoint> points = new ArrayList<>();
                for (JsonNode n : root.path("points")) {
                    WeatherPoint p = new WeatherPoint();
                    p.setSpotId(n.path("spotId").isNull() ? null : n.path("spotId").asLong());
                    p.setName(n.path("name").asText(""));
                    p.setLat(n.path("lat").asDouble());
                    p.setLng(n.path("lng").asDouble());
                    points.add(p);
                }
                subscriptions.put(session, points);
                sendAlerts(session, points);
            }
        } catch (Exception ignored) {
        }
    }

    /** 每 30 分钟推送一次天气提醒 */
    @Scheduled(fixedDelay = 1800000)
    public void broadcastAlerts() {
        if (sessions.isEmpty()) return;
        for (WebSocketSession session : sessions) {
            List<WeatherPoint> points = subscriptions.get(session);
            if (points == null || points.isEmpty()) continue;
            try {
                sendAlerts(session, points);
            } catch (Exception ignored) {
            }
        }
    }

    private void sendAlerts(WebSocketSession session, List<WeatherPoint> points) {
        try {
            List<String> alerts = weatherService.buildAlerts(points);
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "weather_alerts");
            payload.put("alerts", alerts);
            payload.put("updateTime", LocalDateTime.now().format(FMT));
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        } catch (Exception ignored) {
        }
    }
}