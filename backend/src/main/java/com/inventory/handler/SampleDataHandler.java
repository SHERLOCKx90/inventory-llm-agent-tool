package com.inventory.handler;

import com.inventory.db.DatabaseManager;
import com.inventory.util.HttpUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Handles GET /api/inventory/sample and GET /api/inventory/schema
 */
public class SampleDataHandler implements HttpHandler {
    private final DatabaseManager dbManager;

    public SampleDataHandler() {
        this.dbManager = DatabaseManager.getInstance();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtil.handlePreflight(exchange)) {
            return;
        }

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpUtil.sendErrorResponse(exchange, 405, "Method Not Allowed");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        JSONObject response = new JSONObject();

        if (path.endsWith("/schema")) {
            response.put("success", true);
            response.put("schema", dbManager.getDatabaseSchemaSummary());
            HttpUtil.sendJsonResponse(exchange, 200, response);
            return;
        }

        // Return sample preview of inventory items
        try {
            JSONArray sampleRows = dbManager.executeReadOnlyQuery("""
                SELECT p.sku, p.product_name, c.name AS category, w.name AS warehouse, 
                       s.quantity_on_hand, s.reorder_level, p.unit_price
                FROM products p
                JOIN categories c ON p.category_id = c.category_id
                JOIN inventory_stock s ON p.product_id = s.product_id
                JOIN warehouses w ON s.warehouse_id = w.warehouse_id
                ORDER BY s.quantity_on_hand ASC
                LIMIT 10
            """);

            response.put("success", true);
            response.put("sampleInventory", sampleRows);
            HttpUtil.sendJsonResponse(exchange, 200, response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", "Database not connected or empty: " + e.getMessage());
            HttpUtil.sendJsonResponse(exchange, 200, response);
        }
    }
}
