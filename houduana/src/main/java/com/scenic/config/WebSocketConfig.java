package com.scenic.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置：注册 /ws/flow 端点，供客流大屏连接实时数据。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private FlowWebSocketHandler flowWebSocketHandler;

    @Autowired
    private WeatherWebSocketHandler weatherWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(flowWebSocketHandler, "/ws/flow")
                .setAllowedOrigins("*");
        registry.addHandler(weatherWebSocketHandler, "/ws/weather")
                .setAllowedOrigins("*");
    }
}