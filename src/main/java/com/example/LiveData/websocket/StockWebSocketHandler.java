package com.example.LiveData.websocket;


import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class StockWebSocketHandler extends TextWebSocketHandler {

    private final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        System.out.println("✅ Client connected: " + session.getId()
                + "  [total=" + sessions.size() + "]");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        System.out.println("❌ Client disconnected: " + session.getId()
                + "  reason=" + status.getReason()
                + "  [total=" + sessions.size() + "]");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        
        if ("ping".equalsIgnoreCase(message.getPayload().trim())) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage("pong"));
                }
            } catch (IOException e) {
                System.err.println("Pong error: " + e.getMessage());
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        System.err.println("⚠️ Transport error on " + session.getId() + ": " + exception.getMessage());
        sessions.remove(session);
        try { if (session.isOpen()) session.close(); } catch (IOException ignored) {}
    }

   
    public void broadcastStockData(String json) {
        TextMessage message = new TextMessage(json);
        sessions.removeIf(session -> {
            if (!session.isOpen()) return true;
            try {
                session.sendMessage(message);
                return false;
            } catch (IOException e) {
                System.err.println("⚠️ Broadcast failed for " + session.getId() + ": " + e.getMessage());
                return true; // remove dead session
            }
        });
    }

   
    public int getConnectedClientCount() {
        return sessions.size();
    }
}
