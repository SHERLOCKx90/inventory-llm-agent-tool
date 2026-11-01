package com.inventory.handler;

import com.inventory.config.AppConfig;
import com.inventory.util.HttpUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Handles GET /api/config and POST /api/config
 */
public class ConfigHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtil.handlePreflight(exchange)) {
            return;
        }

        String method = exchange.getRequestMethod().toUpperCase();

        if ("GET".equals(method)) {
            JSONObject config = new JSONObject();
            config.put("hasApiKey", !AppConfig.getGeminiApiKey().isBlank());
            config.put("maskedApiKey", maskKey(AppConfig.getGeminiApiKey()));
            config.put("model", AppConfig.getGeminiModel());
            config.put("serverPort", AppConfig.getServerPort());
            HttpUtil.sendJsonResponse(exchange, 200, config);

        } else if ("POST".equals(method)) {
            try {
                String body = HttpUtil.readRequestBody(exchange);
                JSONObject json = new JSONObject(body);

                if (json.has("apiKey")) {
                    AppConfig.setGeminiApiKey(json.getString("apiKey"));
                }
                if (json.has("model")) {
                    AppConfig.setGeminiModel(json.getString("model"));
                }

                JSONObject config = new JSONObject();
                config.put("hasApiKey", !AppConfig.getGeminiApiKey().isBlank());
                config.put("maskedApiKey", maskKey(AppConfig.getGeminiApiKey()));
                config.put("model", AppConfig.getGeminiModel());
                HttpUtil.sendJsonResponse(exchange, 200, config);

            } catch (Exception e) {
                HttpUtil.sendErrorResponse(exchange, 400, "Invalid JSON: " + e.getMessage());
            }

        } else {
            HttpUtil.sendErrorResponse(exchange, 405, "Method Not Allowed");
        }
    }

    private String maskKey(String key) {
        if (key == null || key.isBlank()) return "";
        if (key.length() <= 8) return "********";
        return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
    }
}
