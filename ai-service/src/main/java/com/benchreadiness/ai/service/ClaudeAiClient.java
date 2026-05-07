package com.benchreadiness.ai.service;

import com.benchreadiness.ai.client.ComplianceServiceClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "claude", matchIfMissing = true)
public class ClaudeAiClient implements LlmClient {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ClaudeAiClient.class);

    @Value("${app.claude.api-key:}")
    private String apiKey;

    @Value("${app.claude.model:claude-haiku-4-5}")
    private String questionModel;

    @Value("${app.claude.assessment-model:claude-sonnet-4-5}")
    private String assessmentModel;

    @Value("${app.claude.question-temperature:0.55}")
    private double questionTemperature;

    @Value("${app.claude.assessment-temperature:0.25}")
    private double assessmentTemperature;

    private final ComplianceServiceClient complianceServiceClient;

    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ClaudeAiClient(ComplianceServiceClient complianceServiceClient) {
        this.complianceServiceClient = complianceServiceClient;
    }

    @Override
    public boolean isConfigured() {
        boolean configured = apiKey != null && !apiKey.isBlank();
        log.info("Claude API configured: {}, API key present: {}, API key starts with: {}", 
                configured, apiKey != null, apiKey != null ? apiKey.substring(0, Math.min(10, apiKey.length())) + "..." : "null");
        return configured;
    }

    /**
     * Keep max_tokens bounded per operation to reduce cost and avoid late-interview failures.
     * These values target concise questions and richer assessment summaries.
     */
    private int getMaxTokensForOperation(String operationType) {
        if (operationType == null) return 1000;
        return switch (operationType) {
            case "question" -> 300;
            case "rubric" -> 1000;
            case "assessment" -> 4000;
            case "matching" -> 6000;
            default -> 1000;
        };
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public String chatRubric(String systemPrompt, String userPrompt) throws Exception {
        return chat(systemPrompt, userPrompt, questionModel, questionTemperature, getMaxTokensForOperation("rubric"));
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public String chatQuestion(String systemPrompt, String userPrompt) throws Exception {
        return chat(systemPrompt, userPrompt, questionModel, questionTemperature, getMaxTokensForOperation("question"));
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public String chatQuestionWithSlot(String systemPrompt, String userPrompt, int slot) throws Exception {
        String model = slot <= 5 ? questionModel : assessmentModel;
        return chat(systemPrompt, userPrompt, model, questionTemperature, getMaxTokensForOperation("question"));
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public String chatAssessment(String systemPrompt, String userPrompt) throws Exception {
        return chat(systemPrompt, userPrompt, assessmentModel, assessmentTemperature, getMaxTokensForOperation("assessment"));
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public String chatMatching(String systemPrompt, String userPrompt) throws Exception {
        return chat(systemPrompt, userPrompt, assessmentModel, assessmentTemperature, getMaxTokensForOperation("matching"));
    }

    private String chat(String systemPrompt, String userPrompt, String model,
                        double temperature, int maxTokens) throws Exception {
        return chat(systemPrompt, userPrompt, model, temperature, maxTokens, null, null, null);
    }

    private String chat(String systemPrompt, String userPrompt, String model,
                        double temperature, int maxTokens, String interviewId, String operationType, String userId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("temperature", temperature);
        body.put("system", systemPrompt);
        body.put("messages", List.of(
            Map.of("role", "user", "content", userPrompt)
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CLAUDE_API_URL))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        // Handle 429 rate limit with exponential backoff
        if (response.statusCode() == 429) {
            log.warn("Claude API rate limit hit (429) for operation: {}", operationType);
            int retryAfter = extractRetryAfter(response);
            int waitTime = retryAfter > 0 ? retryAfter : 30000; // Default 30s
            log.info("Waiting {} ms before retry...", waitTime);
            Thread.sleep(waitTime);
            
            // Retry once after waiting
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                throw new RuntimeException("Claude API rate limit exceeded after retry (429)");
            }
        }
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("Claude returned " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        
        // Extract token usage
        JsonNode usage = root.path("usage");
        int promptTokens = usage.path("input_tokens").asInt(0);
        int completionTokens = usage.path("output_tokens").asInt(0);
        
        // Track token usage if interview context provided
        if (interviewId != null && operationType != null) {
            trackTokenUsage(interviewId, operationType, model, promptTokens, completionTokens, userId);
        }
        
        String text = root.path("content").get(0).path("text").asText().trim();
        return stripMarkdownFences(text);
    }
    
    private int extractRetryAfter(HttpResponse<String> response) {
        try {
            String retryAfterHeader = response.headers().firstValue("retry-after").orElse(null);
            if (retryAfterHeader != null) {
                return Integer.parseInt(retryAfterHeader) * 1000; // Convert seconds to ms
            }
            // Try to parse from response body
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode error = root.path("error");
            if (error.has("retry_after")) {
                return (int) (error.path("retry_after").asDouble() * 1000);
            }
        } catch (Exception e) {
            log.warn("Failed to extract retry-after value: {}", e.getMessage());
        }
        return 0;
    }

    private String stripMarkdownFences(String text) {
        // Remove ```json ... ``` or ``` ... ``` wrappers Claude sometimes adds
        String s = text.trim();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```[a-zA-Z]*\\s*", "");
            int end = s.lastIndexOf("```");
            if (end != -1) s = s.substring(0, end);
        }
        return s.trim();
    }

    private void trackTokenUsage(String interviewId, String operationType, String model, 
                                int promptTokens, int completionTokens, String userId) {
        try {
            log.info(">>> AI-SERVICE: Tracking token usage - interviewId: {}, operation: {}, model: {}, promptTokens: {}, completionTokens: {}, userId: {}",
                    interviewId, operationType, model, promptTokens, completionTokens, userId);
            
            Map<String, Object> trackingData = Map.of(
                "interviewId", interviewId,
                "operationType", operationType,
                "modelUsed", model,
                "promptTokens", promptTokens,
                "completionTokens", completionTokens
            );
            
            complianceServiceClient.trackTokenUsage(trackingData, userId != null ? userId : "system");
            log.info("<<< AI-SERVICE: Successfully tracked {} tokens for interview {} ({})", 
                    promptTokens + completionTokens, interviewId, operationType);
        } catch (Exception e) {
            log.error("<<< AI-SERVICE: Error tracking token usage for interview {}: {}", interviewId, e.getMessage(), e);
        }
    }

    public String chatQuestionWithTracking(String systemPrompt, String userPrompt, String interviewId, String userId) throws Exception {
        return chat(systemPrompt, userPrompt, questionModel, questionTemperature, getMaxTokensForOperation("question"), interviewId, "question", userId);
    }

    @Override
    public String chatQuestionWithSlotAndTracking(String systemPrompt, String userPrompt, int slot, String interviewId, String userId) throws Exception {
        String model = slot <= 5 ? questionModel : assessmentModel;
        return chat(systemPrompt, userPrompt, model, questionTemperature, getMaxTokensForOperation("question"), interviewId, "question", userId);
    }

    @Override
    public String chatAssessmentWithTracking(String systemPrompt, String userPrompt, String interviewId, String userId) throws Exception {
        return chat(systemPrompt, userPrompt, assessmentModel, assessmentTemperature, getMaxTokensForOperation("assessment"), interviewId, "assessment", userId);
    }

    @Override
    public String chatRubricWithTracking(String systemPrompt, String userPrompt, String interviewId, String userId) throws Exception {
        return chat(systemPrompt, userPrompt, questionModel, questionTemperature, getMaxTokensForOperation("rubric"), interviewId, "rubric", userId);
    }
}
