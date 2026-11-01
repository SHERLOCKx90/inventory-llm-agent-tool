package com.inventory.handler;

import com.inventory.config.AppConfig;
import com.inventory.db.DatabaseManager;
import com.inventory.util.HttpUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Handles GET /api/db/status and POST /api/db/config
 */
public class DatabaseStatusHandler implements HttpHandler {
    private final DatabaseManager dbManager;

    public DatabaseStatusHandler() {
        this.dbManager = DatabaseManager.getInstance();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtil.handlePreflight(exchange)) {
            return;
        }

        String method = exchange.getRequestMethod().toUpperCase();

        if ("GET".equals(method)) {
            JSONObject status = dbManager.getSystemStatus();
            HttpUtil.sendJsonResponse(exchange, 200, status);

        } else if ("POST".equals(method)) {
            try {
                String body = HttpUtil.readRequestBody(exchange);
                JSONObject json = new JSONObject(body);

                String host = json.optString("host", AppConfig.getDbHost());
                int port = json.optInt("port", AppConfig.getDbPort());
                String dbName = json.optString("database", AppConfig.getDbName());
                String user = json.optString("user", AppConfig.getDbUser());
                String pass = json.optString("password", AppConfig.getDbPassword());

                AppConfig.updateDbConfig(host, port, dbName, user, pass);
                dbManager.reloadDataSource();

                JSONObject status = dbManager.getSystemStatus();
                HttpUtil.sendJsonResponse(exchange, 200, status);
            } catch (Exception e) {
                HttpUtil.sendErrorResponse(exchange, 400, "Invalid configuration payload: " + e.getMessage());
            }

        } else {
            HttpUtil.sendErrorResponse(exchange, 405, "Method Not Allowed");
        }
    }
}
