package com.inventory.agent;

import com.inventory.db.DatabaseManager;
import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.SQLException;

/**
 * Handles the execution of functions/tools invoked by Gemini.
 * Bridges Gemini's tool calls to the MySQL database via JDBC.
 */
public class ToolExecutor {
    private final DatabaseManager dbManager;

    public ToolExecutor() {
        this.dbManager = DatabaseManager.getInstance();
    }

    /**
     * Executes a tool by name and arguments, returning the result object and execution metadata.
     */
    public ToolResult execute(String toolName, JSONObject args) {
        long startTime = System.currentTimeMillis();
        JSONObject resultPayload = new JSONObject();

        if ("execute_inventory_query".equalsIgnoreCase(toolName)) {
            String sqlQuery = args.optString("query", "").trim();
            String explanation = args.optString("explanation", "");

            if (sqlQuery.isEmpty()) {
                resultPayload.put("status", "ERROR");
                resultPayload.put("message", "No SQL query provided in tool arguments.");
                return new ToolResult(toolName, sqlQuery, explanation, resultPayload, null, 0, System.currentTimeMillis() - startTime, false);
            }

            try {
                // Execute query against MySQL database
                JSONArray rows = dbManager.executeReadOnlyQuery(sqlQuery);
                long duration = System.currentTimeMillis() - startTime;

                resultPayload.put("status", "SUCCESS");
                resultPayload.put("query", sqlQuery);
                resultPayload.put("rowCount", rows.length());
                resultPayload.put("data", rows);
                resultPayload.put("executionTimeMs", duration);

                return new ToolResult(toolName, sqlQuery, explanation, resultPayload, rows, rows.length(), duration, true);

            } catch (SQLException e) {
                long duration = System.currentTimeMillis() - startTime;
                resultPayload.put("status", "SQL_ERROR");
                resultPayload.put("message", "MySQL Error: " + e.getMessage());
                resultPayload.put("sqlState", e.getSQLState());
                resultPayload.put("errorCode", e.getErrorCode());
                return new ToolResult(toolName, sqlQuery, explanation, resultPayload, new JSONArray(), 0, duration, false);

            } catch (SecurityException e) {
                long duration = System.currentTimeMillis() - startTime;
                resultPayload.put("status", "SECURITY_ERROR");
                resultPayload.put("message", e.getMessage());
                return new ToolResult(toolName, sqlQuery, explanation, resultPayload, new JSONArray(), 0, duration, false);

            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                resultPayload.put("status", "EXECUTION_ERROR");
                resultPayload.put("message", "Database Error: " + (dbManager.getLastConnectionError() != null ? dbManager.getLastConnectionError() : e.getMessage()));
                return new ToolResult(toolName, sqlQuery, explanation, resultPayload, new JSONArray(), 0, duration, false);
            }

        } else if ("get_database_schema".equalsIgnoreCase(toolName)) {
            String schemaSummary = dbManager.getDatabaseSchemaSummary();
            long duration = System.currentTimeMillis() - startTime;

            resultPayload.put("status", "SUCCESS");
            resultPayload.put("schema", schemaSummary);

            return new ToolResult(toolName, "N/A", "Retrieved inventory database schema", resultPayload, null, 0, duration, true);

        } else {
            long duration = System.currentTimeMillis() - startTime;
            resultPayload.put("status", "UNKNOWN_TOOL");
            resultPayload.put("message", "Tool '" + toolName + "' is not supported.");
            return new ToolResult(toolName, "", "", resultPayload, null, 0, duration, false);
        }
    }

    /**
     * Data record carrying tool execution details and sub-table rows for the frontend.
     */
    public record ToolResult(
            String toolName,
            String query,
            String explanation,
            JSONObject rawPayload,
            JSONArray subTable,
            int rowCount,
            long executionTimeMs,
            boolean success
    ) {
        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("toolName", toolName);
            json.put("query", query);
            json.put("explanation", explanation);
            json.put("rowCount", rowCount);
            json.put("executionTimeMs", executionTimeMs);
            json.put("success", success);
            if (subTable != null) {
                json.put("subTable", subTable);
            }
            if (rawPayload.has("message")) {
                json.put("message", rawPayload.getString("message"));
            }
            return json;
        }
    }
}
