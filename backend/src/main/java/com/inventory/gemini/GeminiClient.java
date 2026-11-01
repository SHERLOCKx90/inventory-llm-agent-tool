package com.inventory.gemini;

import com.inventory.config.AppConfig;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Communicates with the Google Gemini REST API using Java 11+ HttpClient.
 * Includes resilience with fallback models if the primary model is busy (503) or unavailable.
 */
public class GeminiClient {
    private static final String GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private final HttpClient httpClient;

    public GeminiClient() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    /**
     * Calls Gemini generateContent with automatic retry and model fallback.
     */
    public JSONObject generateContent(JSONObject requestBody, String apiKeyOverride) throws IOException, InterruptedException {
        String apiKey = (apiKeyOverride != null && !apiKeyOverride.isBlank())
                ? apiKeyOverride.trim()
                : AppConfig.getGeminiApiKey();

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Gemini API Key is missing. Please provide it via GEMINI_API_KEY environment variable or in the UI settings.");
        }

        String primaryModel = AppConfig.getGeminiModel();
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        if (primaryModel != null && !primaryModel.isBlank()) {
            set.add(primaryModel);
        }
        set.add("gemini-3.5-flash-lite");
        set.add("gemini-flash-lite-latest");
        set.add("gemini-3-flash-preview");
        List<String> modelsToTry = new ArrayList<>(set);

        String lastError = null;

        for (String model : modelsToTry) {
            String url = GEMINI_API_BASE + model + ":generateContent?key=" + apiKey;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int statusCode = response.statusCode();
                String responseBody = response.body();

                if (statusCode >= 200 && statusCode < 300) {
                    return new JSONObject(responseBody);
                }

                // If 503 (high demand), 404 (not found), or 429 (quota exceeded on this specific model), try fallback
                String errorMsg = "Gemini API (" + model + ") error " + statusCode;
                try {
                    JSONObject errJson = new JSONObject(responseBody);
                    if (errJson.has("error")) {
                        errorMsg = errJson.getJSONObject("error").optString("message", errorMsg);
                    }
                } catch (Exception ignored) {
                    errorMsg += ": " + responseBody;
                }

                lastError = errorMsg;
                // If it's a 400 bad request or 401 unauthorized key, stop retrying. If 429, 503, or 404, fallback to next model!
                if (statusCode != 503 && statusCode != 404 && statusCode != 429) {
                    break;
                }

            } catch (Exception e) {
                String detail = (e.getMessage() != null && !e.getMessage().isBlank()) ? e.getMessage() : e.getClass().getSimpleName();
                lastError = "Connection error with " + model + ": " + detail;
            }
        }

        throw new IOException(lastError != null ? lastError : "Failed to get response from Gemini API");
    }
}
