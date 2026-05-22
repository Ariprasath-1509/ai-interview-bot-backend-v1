package com.benchreadiness.ai.service;

import com.benchreadiness.ai.client.ComplianceServiceClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ollama-backed implementation. Uses POST {baseUrl}/api/chat.
 */
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "ollama")
public class OllamaAiClient implements LlmClient {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OllamaAiClient.class);

    @Value("${app.ollama.base-url:http://127.0.0.1:11434}")
    private String baseUrl;

    @Value("${app.ollama.question-model:qwen2.5:7b}")
    private String questionModel;

    @Value("${app.ollama.assessment-model:qwen2.5:32b}")
    private String assessmentModel;

    @Value("${app.ollama.rubric-model:qwen2.5:14b}")
    private String rubricModel;

    @Value("${app.ollama.matching-model:qwen2.5:32b}")
    private String matchingModel;

    @Value("${app.ollama.question-temperature:0.55}")
    private double questionTemperature;

    @Value("${app.ollama.assessment-temperature:0.3}")
    private double assessmentTemperature;

    @Value("${app.ollama.rubric-temperature:0.1}")
    private double rubricTemperature;

    @Value("${app.ollama.timeout-seconds:600}")
    private int timeoutSeconds;

    @Value("${app.ollama.num-parallel-threads:4}")
    private int numParallelThreads;

    private final ComplianceServiceClient complianceServiceClient;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OllamaAiClient(ComplianceServiceClient complianceServiceClient) {
        this.complianceServiceClient = complianceServiceClient;
    }

    @Override
    public boolean isConfigured() {
        boolean configured = baseUrl != null && !baseUrl.isBlank() 
                && questionModel != null && !questionModel.isBlank();
        log.info("Ollama configured: {}, baseUrl={}, questionModel={}, assessmentModel={}, rubricModel={}, matchingModel={}", 
                configured, baseUrl, questionModel, assessmentModel, rubricModel, matchingModel);
        return configured;
    }

    @Override
    public String chatQuestion(String systemPrompt, String userPrompt) throws Exception {
        return chat(systemPrompt, userPrompt, questionModel, questionTemperature, null, null, null);
    }

    @Override
    public String chatQuestionWithSlotAndTracking(String systemPrompt, String userPrompt, int slot, String interviewId, String userId) throws Exception {
        return chat(systemPrompt, userPrompt, questionModel, questionTemperature, interviewId, "question", userId);
    }

    @Override
    public String chatAssessmentWithTracking(String systemPrompt, String userPrompt, String interviewId, String userId) throws Exception {
        return chat(systemPrompt, userPrompt, assessmentModel, assessmentTemperature, interviewId, "assessment", userId);
    }

    @Override
    public String chatRubricWithTracking(String systemPrompt, String userPrompt, String interviewId, String userId) throws Exception {
        return chat(systemPrompt, userPrompt, rubricModel, rubricTemperature, interviewId, "rubric", userId);
    }

    @Override
    public String chatMatching(String systemPrompt, String userPrompt) throws Exception {
        return chat(systemPrompt, userPrompt, matchingModel, assessmentTemperature, null, null, null);
    }

    private String chat(String systemPrompt, String userPrompt, String model,
                        double temperature, String interviewId, String operationType, String userId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", false);
        body.put("options", Map.of(
                "temperature", Math.max(0.0, Math.min(2.0, temperature)),
                "num_thread", numParallelThreads,
                "stop", List.of("```", "<think>", "</think>")
        ));
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));

        String url = (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl) + "/api/chat";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(Math.max(30, Math.min(900, timeoutSeconds))))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Ollama returned " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String text = root.path("message").path("content").asText("").trim();

        if (interviewId != null && operationType != null) {
            // Ollama doesn't provide token counts in this response format; track 0s to keep the pipeline consistent.
            trackTokenUsage(interviewId, operationType, model, 0, 0, userId);
        }
        return stripMarkdownFences(text);
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
            log.warn("Token tracking failed for interview {}: {}", interviewId, e.getMessage());
        }
    }
}

