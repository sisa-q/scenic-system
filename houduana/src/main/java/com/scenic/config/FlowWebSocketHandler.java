package com.scenic.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scenic.service.FlowStatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 客流大屏 WebSocket 处理器（实时数据引擎后端）
 *
 * 大屏连接 /ws/flow 后，每 5 秒向其推送一次真实客流数据：
 *   { "currentVisitors": 当前在园, "todayEntered": 今日入园, "updateTime": ... }
 * 数据来自 /api/flow/realtime（基于票务+核销推导，无需硬件）。
 */
@Component
public class FlowWebSocketHandler extends TextWebSocketHandler {

    /** 已连接的大屏会话集合（线程安全） */
    private static final CopyOnWriteArraySet<WebSocketSession> SESSIONS = new CopyOnWriteArraySet<>();

    @Autowired
    private FlowStatService flowStatService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        SESSIONS.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SESSIONS.remove(session);
    }

    /** 每 5 秒向所有大屏推送一次真实客流数据 */
    @Scheduled(fixedDelay = 5000)
    public void broadcastRealtime() {
        if (SESSIONS.isEmpty()) {
            return;
        }
        try {
            Map<String, Object> realtime = flowStatService.getRealtime();
            String json = objectMapper.writeValueAsString(realtime);
            for (WebSocketSession session : SESSIONS) {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(json));
                    }
                } catch (Exception ignored) {
                    SESSIONS.remove(session);
                }
            }
        } catch (Exception ignored) {
            // 推送失败不影响系统
        }
    }
}