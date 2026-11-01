package com.inventory.handler;

import com.inventory.agent.InventoryAgent;
import com.inventory.util.HttpUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Handles POST /api/chat
 * Takes { "prompt": "...", "apiKey": "..." } and invokes the InventoryAgent.
 */
public class ChatHandler implements HttpHandler {
    private final InventoryAgent agent;

    public ChatHandler() {
        this.agent = new InventoryAgent();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtil.handlePreflight(exchange)) {
            return;
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpUtil.sendErrorResponse(exchange, 405, "Method Not Allowed. Use POST.");
            return;
        }

        try {
            String bodyStr = HttpUtil.readRequestBody(exchange);
            if (bodyStr.isBlank()) {
                HttpUtil.sendErrorResponse(exchange, 400, "Request body is empty.");
                return;
            }

            JSONObject requestJson = new JSONObject(bodyStr);
            String prompt = requestJson.optString("prompt", "").trim();
            String apiKey = requestJson.optString("apiKey", "").trim();

            if (prompt.isEmpty()) {
                HttpUtil.sendErrorResponse(exchange, 400, "Field 'prompt' is required.");
                return;
            }

            // Run agent process (dynamic prompt -> Gemini -> Tool Call -> MySQL -> Gemini -> Final Response)
            InventoryAgent.AgentResponse response = agent.processPrompt(prompt, apiKey);

            int status = response.success() ? 200 : 500;
            HttpUtil.sendJsonResponse(exchange, status, response.toJson());

        } catch (Exception e) {
            HttpUtil.sendErrorResponse(exchange, 500, "Internal Server Error: " + e.getMessage());
        }
    }
}
