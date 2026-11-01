package com.inventory.agent;

import com.inventory.db.DatabaseManager;
import com.inventory.gemini.GeminiClient;
import com.inventory.gemini.ToolDefinition;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrator implementing the full MCP/Function-Calling loop:
 * 1. Takes dynamic natural language prompt.
 * 2. Consults Gemini with Function Declarations + Schema context.
 * 3. Intercepts functionCall decisions.
 * 4. Executes read-only SQL via ToolExecutor against MySQL.
 * 5. Passes extracted sub-table back to Gemini.
 * 6. Synthesizes final grounded answer.
 */
public class InventoryAgent {
    private final GeminiClient geminiClient;
    private final ToolExecutor toolExecutor;
    private final DatabaseManager dbManager;

    public InventoryAgent() {
        this.geminiClient = new GeminiClient();
        this.toolExecutor = new ToolExecutor();
        this.dbManager = DatabaseManager.getInstance();
    }

    public AgentResponse processPrompt(String userPrompt, String apiKeyOverride) {
        if (userPrompt == null || userPrompt.isBlank()) {
            return AgentResponse.error("Prompt cannot be empty");
        }

        List<ToolExecutor.ToolResult> executedTools = new ArrayList<>();

        try {
            // Step 1: Prepare System Instruction with DB Schema
            JSONObject systemInstruction = new JSONObject();
            JSONArray sysParts = new JSONArray();
            JSONObject sysText = new JSONObject();
            sysText.put("text", """
                You are the Chief Inventory Intelligence & Business Analyst for an enterprise warehouse & retail distribution network.
                You have direct read-only access to the MySQL database `inventory_db`.
                
                YOUR OBJECTIVE:
                Deliver concrete, intelligent, and decision-ready business analysis for executives, stakeholders, and warehouse managers.
                
                DATABASE SCHEMA:
                """ + dbManager.getDatabaseSchemaSummary() + """
                
                INSTRUCTIONS:
                1. Always call `execute_inventory_query` to fetch real data (e.g. stock levels, reorder thresholds, movements, warehouses, categories).
                2. Do multiple queries if necessary (e.g. comparing stock vs movements, checking warehouses, analyzing supplier performance).
                3. IN YOUR FINAL BUSINESS ANALYSIS:
                   - Provide a structured, insightful executive assessment (Executive Summary, Key Findings, Risk Assessment, and Actionable Recommendations).
                   - Emphasize financial, supply chain, and operational implications (e.g., capital tied up in slow-moving stock, risk of stockouts for high-demand items, vendor reliance).
                   - Highlight key metrics, products, and warehouse locations using bolding and clear bullet points.
                   - Do NOT just talk about SQL or raw tables in your main text. Stakeholders want intelligent insights and business conclusions!
                """);
            sysParts.put(sysText);
            systemInstruction.put("parts", sysParts);

            // Step 2: Build conversation contents
            JSONArray contents = new JSONArray();
            JSONObject initialUserMessage = new JSONObject();
            initialUserMessage.put("role", "user");
            JSONArray initialParts = new JSONArray();
            initialParts.put(new JSONObject().put("text", userPrompt));
            initialUserMessage.put("parts", initialParts);
            contents.put(initialUserMessage);

            // Tool calling loop (up to 5 turns)
            int maxTurns = 5;
            String finalAnswer = null;

            for (int turn = 0; turn < maxTurns; turn++) {
                JSONObject requestPayload = new JSONObject();
                requestPayload.put("systemInstruction", systemInstruction);
                requestPayload.put("tools", ToolDefinition.getToolsDeclaration());
                requestPayload.put("contents", contents);

                JSONObject geminiResponse = geminiClient.generateContent(requestPayload, apiKeyOverride);

                // Parse candidates
                JSONArray candidates = geminiResponse.optJSONArray("candidates");
                if (candidates == null || candidates.isEmpty()) {
                    return AgentResponse.error("Gemini returned no candidates in response: " + geminiResponse);
                }

                JSONObject candidate = candidates.getJSONObject(0);
                JSONObject candidateContent = candidate.optJSONObject("content");
                if (candidateContent == null) {
                    return AgentResponse.error("Candidate missing content: " + candidate);
                }

                JSONArray parts = candidateContent.optJSONArray("parts");
                if (parts == null || parts.isEmpty()) {
                    return AgentResponse.error("No parts found in model response");
                }

                // Check for functionCall
                JSONObject functionCall = null;
                StringBuilder textBuilder = new StringBuilder();

                for (int i = 0; i < parts.length(); i++) {
                    JSONObject part = parts.getJSONObject(i);
                    if (part.has("functionCall")) {
                        functionCall = part.getJSONObject("functionCall");
                    }
                    if (part.has("text")) {
                        textBuilder.append(part.getString("text"));
                    }
                }

                if (functionCall != null) {
                    // Gemini requested a tool execution!
                    String toolName = functionCall.optString("name");
                    JSONObject args = functionCall.optJSONObject("args");
                    if (args == null) args = new JSONObject();

                    // Execute tool via ToolExecutor
                    ToolExecutor.ToolResult result = toolExecutor.execute(toolName, args);
                    executedTools.add(result);

                    // Append model's complete candidate content verbatim (preserves thoughtSignature, id, and thinking parts)
                    contents.put(candidateContent);

                    // Append functionResponse turn to conversation history
                    JSONObject functionResponseTurn = new JSONObject();
                    functionResponseTurn.put("role", "user");

                    JSONObject functionResponsePart = new JSONObject();
                    JSONObject funcResponseObj = new JSONObject();
                    funcResponseObj.put("name", toolName);
                    if (functionCall.has("id")) {
                        funcResponseObj.put("id", functionCall.getString("id"));
                    }

                    JSONObject responseWrapper = new JSONObject();
                    responseWrapper.put("result", result.rawPayload());
                    funcResponseObj.put("response", responseWrapper);

                    functionResponsePart.put("functionResponse", funcResponseObj);
                    functionResponseTurn.put("parts", new JSONArray().put(functionResponsePart));
                    contents.put(functionResponseTurn);

                    // Continue loop to let Gemini read the sub-table response and formulate answer
                } else {
                    // No function call was requested, model provided direct answer
                    finalAnswer = textBuilder.toString();
                    break;
                }
            }

            // If all tool turns were consumed without emitting a final text answer, perform a final synthesis call
            if (finalAnswer == null || finalAnswer.isBlank()) {
                JSONObject synthMessage = new JSONObject();
                synthMessage.put("role", "user");
                JSONArray synthParts = new JSONArray();
                synthParts.put(new JSONObject().put("text", 
                    "You have retrieved all necessary database records above. " +
                    "Now provide your full, concrete, intelligent business analysis for stakeholders. " +
                    "Include: 1) Executive Summary, 2) Key Data Findings & Trends, 3) Risk Assessment (low stock/stagnant inventory), and 4) Actionable Business Recommendations."
                ));
                synthMessage.put("parts", synthParts);
                contents.put(synthMessage);

                JSONObject synthPayload = new JSONObject();
                synthPayload.put("systemInstruction", systemInstruction);
                synthPayload.put("contents", contents);

                JSONObject synthResponse = geminiClient.generateContent(synthPayload, apiKeyOverride);
                JSONArray synthCandidates = synthResponse.optJSONArray("candidates");
                if (synthCandidates != null && !synthCandidates.isEmpty()) {
                    JSONObject synthContent = synthCandidates.getJSONObject(0).optJSONObject("content");
                    if (synthContent != null) {
                        JSONArray parts = synthContent.optJSONArray("parts");
                        if (parts != null) {
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < parts.length(); i++) {
                                if (parts.getJSONObject(i).has("text")) {
                                    sb.append(parts.getJSONObject(i).getString("text"));
                                }
                            }
                            finalAnswer = sb.toString();
                        }
                    }
                }
            }

            if (finalAnswer == null || finalAnswer.isBlank()) {
                finalAnswer = "Analysis completed based on the retrieved inventory data.";
            }

            return new AgentResponse(true, finalAnswer, executedTools, null);

        } catch (Exception e) {
            return AgentResponse.error("Agent execution error: " + e.getMessage());
        }
    }

    /**
     * Complete Agent Response structure returned to frontend.
     */
    public record AgentResponse(
            boolean success,
            String answer,
            List<ToolExecutor.ToolResult> toolCalls,
            String error
    ) {
        public static AgentResponse error(String message) {
            return new AgentResponse(false, null, List.of(), message);
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("success", success);
            if (answer != null) json.put("answer", answer);
            if (error != null) json.put("error", error);

            JSONArray toolsJson = new JSONArray();
            if (toolCalls != null) {
                for (ToolExecutor.ToolResult tr : toolCalls) {
                    toolsJson.put(tr.toJson());
                }
            }
            json.put("toolCalls", toolsJson);
            return json;
        }
    }
}
