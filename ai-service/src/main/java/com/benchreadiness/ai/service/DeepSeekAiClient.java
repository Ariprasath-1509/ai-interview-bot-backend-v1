package com.benchreadiness.ai.service;

import com.benchreadiness.ai.client.ComplianceServiceClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek-backed implementation. Uses the OpenAI-compatible
 * POST {baseUrl}/chat/completions endpoint (Bearer auth, choices[0].message.content).
 */
@Component
public class DeepSeekAiClient implements LlmClient {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DeepSeekAiClient.class);

    @Value("${app.deepseek.api-key:}")
    private String apiKey;

    @Value("${app.deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${app.deepseek.model:deepseek-chat}")
    private String model;

    @Value("${app.deepseek.question-temperature:0.55}")
    private double questionTemperature;

    @Value("${app.deepseek.assessment-temperature:0.25}")
    private double assessmentTemperature;

    private final ComplianceServiceClient complianceServiceClient;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;
    private volatile boolean authFailed = false;
    private volatile Boolean configuredCache = null;

    public DeepSeekAiClient(ComplianceServiceClient complianceServiceClient, ObjectMapper objectMapper) {
        this.complianceServiceClient = complianceServiceClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isConfigured() {
        if (authFailed) {
            return false;
        }
        if (configuredCache == null) {
            configuredCache = apiKey != null && !apiKey.isBlank();
            log.info("DeepSeek API configured: {}", configuredCache);
        }
        return configuredCache;
    }

    /**
     * Keep max_tokens bounded per operation to reduce cost and avoid late-interview failures.
     */
    private int getMaxTokensForOperation(String operationType) {
        if (operationType == null) return 1000;
        return switch (operationType) {
            case "question" -> 300;
            case "rubric" -> 2500;
            case "assessment" -> 4000;
            case "matching" -> 3200;
            default -> 1000;
        };
    }

    @Override
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public String chatQuestion(String systemPrompt, String userPrompt) throws Exception {
        return chat(systemPrompt, userPrompt, questionTemperature, getMaxTokensForOperation("question"), null, null, null);
    }

    @Override
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public String chatQuestionWithSlotAndTracking(String systemPrompt, String userPrompt, int slot, String interviewId, String userId) throws Exception {
        return chat(systemPrompt, userPrompt, questionTemperature, getMaxTokensForOperation("question"), interviewId, "question", userId);
    }

    @Override
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public String chatAssessmentWithTracking(String systemPrompt, String userPrompt, String interviewId, String userId) throws Exception {
        return chat(systemPrompt, userPrompt, assessmentTemperature, getMaxTokensForOperation("assessment"), interviewId, "assessment", userId);
    }

    @Override
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public String chatRubricWithTracking(String systemPrompt, String userPrompt, String interviewId, String userId) throws Exception {
        return chat(systemPrompt, userPrompt, assessmentTemperature, getMaxTokensForOperation("rubric"), interviewId, "rubric", userId);
    }

    @Override
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public String chatMatching(String systemPrompt, String userPrompt) throws Exception {
        return chat(systemPrompt, userPrompt, assessmentTemperature, dynamicMatchingMaxTokens(userPrompt), null, null, null);
    }

    @Override
    @Retryable(maxAttempts = 2, backoff = @Backoff(delay = 2000, multiplier = 2))
    public String chatDigest(String systemPrompt, String userPrompt) throws Exception {
        return chat(systemPrompt, userPrompt, 0.1, 8000, null, null, null);
    }

    private String chat(String systemPrompt, String userPrompt, double temperature, int maxTokens,
                        String interviewId, String operationType, String userId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("temperature", temperature);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));

        String url = (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl) + "/chat/completions";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Handle 429 rate limit with a single backoff retry
        if (response.statusCode() == 429) {
            log.warn("DeepSeek API rate limit hit (429) for operation: {}", operationType);
            int retryAfter = extractRetryAfter(response);
            int waitTime = retryAfter > 0 ? retryAfter : 30000; // Default 30s
            log.info("Waiting {} ms before retry...", waitTime);
            Thread.sleep(waitTime);

            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                throw new RuntimeException("DeepSeek returned 429: rate limit exceeded after retry");
            }
        }

        if (response.statusCode() == 401) {
            authFailed = true;
            configuredCache = false;
            throw new RuntimeException("DeepSeek returned 401: authentication failed (invalid API key)");
        }

        if (response.statusCode() != 200) {
            throw new RuntimeException("DeepSeek returned " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());

        JsonNode usage = root.path("usage");
        int promptTokens = usage.path("prompt_tokens").asInt(0);
        int completionTokens = usage.path("completion_tokens").asInt(0);

        if (interviewId != null && operationType != null) {
            trackTokenUsage(interviewId, operationType, model, promptTokens, completionTokens, userId);
        }

        String text = root.path("choices").get(0).path("message").path("content").asText().trim();
        return stripMarkdownFences(text);
    }

    private int extractRetryAfter(HttpResponse<String> response) {
        try {
            String retryAfterHeader = response.headers().firstValue("retry-after").orElse(null);
            if (retryAfterHeader != null) {
                return Integer.parseInt(retryAfterHeader) * 1000;
            }
        } catch (Exception e) {
            log.warn("Failed to extract retry-after value: {}", e.getMessage());
        }
        return 0;
    }

    private String stripMarkdownFences(String text) {
        String s = text.trim();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```[a-zA-Z]*\\s*", "");
            int end = s.lastIndexOf("```");
            if (end != -1) s = s.substring(0, end);
        }
        return s.trim();
    }

    private int dynamicMatchingMaxTokens(String userPrompt) {
        try {
            JsonNode node = objectMapper.readTree(userPrompt);
            int candidateCount = node.isArray() ? node.size() : 1;
            int dynamic = 2000 + (candidateCount * 400);
            return Math.max(2400, Math.min(8000, dynamic));
        } catch (Exception ignored) {
            return getMaxTokensForOperation("matching");
        }
    }

    private void trackTokenUsage(String interviewId, String operationType, String model,
                                int promptTokens, int completionTokens, String userId) {
        try {
            Map<String, Object> trackingData = Map.of(
                "interviewId", interviewId,
                "operationType", operationType,
                "modelUsed", model,
                "promptTokens", promptTokens,
                "completionTokens", completionTokens
            );
            complianceServiceClient.trackTokenUsage(trackingData, userId != null ? userId : "system");
        } catch (Exception e) {
            log.error("<<< AI-SERVICE: Error tracking token usage for interview {}: {}", interviewId, e.getMessage(), e);
        }
    }
}
