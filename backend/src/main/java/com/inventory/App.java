package com.inventory;

import com.inventory.config.AppConfig;
import com.inventory.handler.*;
import com.inventory.util.HttpUtil;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Main application entrypoint.
 * Pure Core Java HTTP Server using com.sun.net.httpserver.HttpServer.
 * NO Spring Boot or heavy frameworks.
 */
public class App {

    public static void main(String[] args) {
        int port = AppConfig.getServerPort();

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

            // Register API Handlers
            server.createContext("/api/chat", new ChatHandler());
            server.createContext("/api/db/status", new DatabaseStatusHandler());
            server.createContext("/api/db/config", new DatabaseStatusHandler());
            server.createContext("/api/config", new ConfigHandler());
            server.createContext("/api/inventory/sample", new SampleDataHandler());
            server.createContext("/api/inventory/schema", new SampleDataHandler());

            // Simple Health Endpoint
            server.createContext("/api/health", exchange -> {
                if (HttpUtil.handlePreflight(exchange)) return;
                JSONObject health = new JSONObject();
                health.put("status", "UP");
                health.put("server", "Core Java HttpServer (Java " + System.getProperty("java.version") + ")");
                health.put("timestamp", System.currentTimeMillis());
                HttpUtil.sendJsonResponse(exchange, 200, health);
            });

            // Root info handler
            server.createContext("/", exchange -> {
                if (HttpUtil.handlePreflight(exchange)) return;
                if (!"/".equals(exchange.getRequestURI().getPath())) {
                    HttpUtil.sendErrorResponse(exchange, 404, "Endpoint not found: " + exchange.getRequestURI().getPath());
                    return;
                }
                JSONObject info = new JSONObject();
                info.put("service", "Inventory Intelligence Agent - Core Java Server");
                info.put("status", "Running");
                info.put("geminiModel", AppConfig.getGeminiModel());
                info.put("hasApiKey", !AppConfig.getGeminiApiKey().isBlank());
                HttpUtil.sendJsonResponse(exchange, 200, info);
            });

            // Modern Java Virtual Threads Executor for high-concurrency non-blocking I/O
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.start();

            printBanner(port);

        } catch (Exception e) {
            System.err.println("Fatal: Failed to start Core Java server on port " + port);
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printBanner(int port) {
        System.out.println("==================================================================");
        System.out.println("       INVENTORY AGENT - PURE CORE JAVA SERVER STARTED           ");
        System.out.println("==================================================================");
        System.out.println(" Server Port   : http://localhost:" + port);
        System.out.println(" Java Runtime  : " + System.getProperty("java.runtime.name") + " (" + System.getProperty("java.version") + ")");
        System.out.println(" Architecture  : Pure com.sun.net.httpserver.HttpServer + JDBC");
        System.out.println(" Gemini Model  : " + AppConfig.getGeminiModel());
        System.out.println(" API Key Set?  : " + (!AppConfig.getGeminiApiKey().isBlank() ? "YES" : "NO (Configure via GEMINI_API_KEY or UI)"));
        System.out.println(" MySQL Host    : " + AppConfig.getDbHost() + ":" + AppConfig.getDbPort() + "/" + AppConfig.getDbName());
        System.out.println("------------------------------------------------------------------");
        System.out.println(" Endpoints:");
        System.out.println("  - POST http://localhost:" + port + "/api/chat");
        System.out.println("  - GET  http://localhost:" + port + "/api/db/status");
        System.out.println("  - GET  http://localhost:" + port + "/api/config");
        System.out.println("  - GET  http://localhost:" + port + "/api/inventory/sample");
        System.out.println("  - GET  http://localhost:" + port + "/api/inventory/schema");
        System.out.println("  - GET  http://localhost:" + port + "/api/health");
        System.out.println("==================================================================");
    }
}
