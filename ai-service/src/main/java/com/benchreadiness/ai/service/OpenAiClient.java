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
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@Component
public class OpenAiClient {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OpenAiClient.class);

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

    @Value("${app.claude.question-max-tokens:300}")
    private int questionMaxTokens;

    @Value("${app.claude.assessment-max-tokens:4000}")
    private int assessmentMaxTokens;

    @Value("${app.claude.rubric-max-tokens:1000}")
    private int rubricMaxTokens;

    @Value("${app.claude.matching-max-tokens:6000}")
    private int matchingMaxTokens;

    private final ComplianceServiceClient complianceServiceClient;

    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;

    public OpenAiClient(ComplianceServiceClient complianceServiceClient, ObjectMapper objectMapper) {
        this.complianceServiceClient = complianceServiceClient;
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        boolean configured = apiKey != null && !apiKey.isBlank();
        log.info("Claude API configured: {}, API key present: {}, API key starts with: {}", 
                configured, apiKey != null, apiKey != null ? apiKey.substring(0, Math.min(10, apiKey.length())) + "..." : "null");
        return configured;
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public String chatRubric(String systemPrompt, String userPrompt) throws Exception {
        return chat(systemPrompt, userPrompt, questionModel, questionTemperature, rubricMaxTokens);
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public String chatQuestion(String systemPrompt, String userPrompt) throws Exception {
        return chat(systemPrompt, userPrompt, questionModel, questionTemperature, questionMaxTokens);
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public String chatQuestionWithSlot(String systemPrompt, String userPrompt, int slot) throws Exception {
        return chat(systemPrompt, userPrompt, questionModel, questionTemperature, questionMaxTokens);
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public String chatAssessment(String systemPrompt, String userPrompt) throws Exception {
        // Use the system prompt as-is - it already contains the proper JSON structure
        return chat(systemPrompt, userPrompt, assessmentModel, assessmentTemperature, assessmentMaxTokens);
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public String chatMatching(String systemPrompt, String userPrompt) throws Exception {
        return chat(systemPrompt, userPrompt, assessmentModel, assessmentTemperature, dynamicMatchingMaxTokens(userPrompt));
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

    private int dynamicMatchingMaxTokens(String userPrompt) {
        try {
            JsonNode node = objectMapper.readTree(userPrompt);
            int candidateCount = node.isArray() ? node.size() : 1;
            int dynamic = 1200 + (candidateCount * 220);
            return Math.max(1400, Math.min(matchingMaxTokens, dynamic));
        } catch (Exception ignored) {
            return Math.min(matchingMaxTokens, 3200);
        }
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

    // Public methods for tracking with interview context
    public String chatQuestionWithTracking(String systemPrompt, String userPrompt, String interviewId, String userId) throws Exception {
        return chat(systemPrompt, userPrompt, questionModel, questionTemperature, questionMaxTokens, interviewId, "question", userId);
    }

    public String chatQuestionWithSlotAndTracking(String systemPrompt, String userPrompt, int slot, String interviewId, String userId) throws Exception {
        return chat(systemPrompt, userPrompt, questionModel, questionTemperature, questionMaxTokens, interviewId, "question", userId);
    }

    public String chatAssessmentWithTracking(String systemPrompt, String userPrompt, String interviewId, String userId) throws Exception {
        // Use the system prompt as-is from AssessmentService - it already contains the proper JSON structure
        return chat(systemPrompt, userPrompt, assessmentModel, assessmentTemperature, assessmentMaxTokens, interviewId, "assessment", userId);
    }

    public String chatRubricWithTracking(String systemPrompt, String userPrompt, String interviewId, String userId) throws Exception {
        return chat(systemPrompt, userPrompt, questionModel, questionTemperature, rubricMaxTokens, interviewId, "rubric", userId);
    }
}
