package com.inventory.gemini;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Defines MCP / Function Calling tools provided to Gemini.
 */
public class ToolDefinition {

    /**
     * Builds the tools declaration array for Gemini API.
     */
    public static JSONArray getToolsDeclaration() {
        JSONArray tools = new JSONArray();
        JSONObject toolGroup = new JSONObject();
        JSONArray functionDeclarations = new JSONArray();

        // 1. Tool: execute_inventory_query
        JSONObject execQueryTool = new JSONObject();
        execQueryTool.put("name", "execute_inventory_query");
        execQueryTool.put("description", "Executes a safe, read-only SELECT SQL query against the MySQL inventory database to fetch relevant records (products, warehouses, stock levels, suppliers, categories, movements). Returns matching rows as an extracted sub-table.");

        JSONObject params = new JSONObject();
        params.put("type", "OBJECT");
        JSONObject props = new JSONObject();

        JSONObject queryProp = new JSONObject();
        queryProp.put("type", "STRING");
        queryProp.put("description", "A valid MySQL SELECT query formatted according to the database schema. Must only be SELECT statements.");
        props.put("query", queryProp);

        JSONObject explanationProp = new JSONObject();
        explanationProp.put("type", "STRING");
        explanationProp.put("description", "A brief explanation of why this query is needed to answer the user prompt.");
        props.put("explanation", explanationProp);

        params.put("properties", props);
        params.put("required", new JSONArray().put("query"));
        execQueryTool.put("parameters", params);

        functionDeclarations.put(execQueryTool);

        // 2. Tool: get_database_schema
        JSONObject schemaTool = new JSONObject();
        schemaTool.put("name", "get_database_schema");
        schemaTool.put("description", "Returns the complete database schema with tables, columns, data types, and relationships in the inventory database.");
        JSONObject emptyParams = new JSONObject();
        emptyParams.put("type", "OBJECT");
        emptyParams.put("properties", new JSONObject());
        schemaTool.put("parameters", emptyParams);

        functionDeclarations.put(schemaTool);

        toolGroup.put("functionDeclarations", functionDeclarations);
        tools.put(toolGroup);
        return tools;
    }
}
