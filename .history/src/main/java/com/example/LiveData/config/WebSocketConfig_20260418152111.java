package com.example.LiveData.config;

import com.example.LiveData.websocket.StockWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private StockWebSocketHandler stockWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Registering the exact path used by the frontend
        registry.addHandler(stockWebSocketHandler, "/ws/stocks/websocket")
                .setAllowedOrigins("*");
        // DO NOT add .withSockJS() here as the frontend uses the native WebSocket API
    }
}