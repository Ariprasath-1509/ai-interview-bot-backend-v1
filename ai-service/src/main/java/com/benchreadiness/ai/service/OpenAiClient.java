package com.benchreadiness.ai.service;

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

    @Value("${app.claude.assessment-max-tokens:1200}")
    private int assessmentMaxTokens;

    @Value("${app.claude.rubric-max-tokens:1000}")
    private int rubricMaxTokens;

    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
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
        // Slots 1-5: use haiku (fast, cheap), Slots 6-10: use sonnet (higher quality)
        String model = slot <= 5 ? questionModel : assessmentModel;
        return chat(systemPrompt, userPrompt, model, questionTemperature, questionMaxTokens);
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public String chatAssessment(String systemPrompt, String userPrompt) throws Exception {
        // Claude doesn't support structured output schema like OpenAI — instruct via system prompt instead
        String assessmentSystem = systemPrompt + "\n\nYou MUST respond with valid JSON only. No explanation, no markdown, no code block. Raw JSON matching this exact structure:\n" +
            "{\"technicalKnowledge\":{\"score\":1-5,\"rationale\":\"...\"},\"communication\":{\"score\":1-5,\"rationale\":\"...\"}," +
            "\"jdFit\":{\"score\":1-5,\"rationale\":\"...\"},\"proposedVerdict\":\"READY|NEEDS_1_WEEK_PREP|NEEDS_RESKILLING|MISMATCH_WITH_JD\"," +
            "\"summary\":\"...\",\"strengths\":[\"...\"],\"gaps\":[\"...\"]}";
        return chat(assessmentSystem, userPrompt, assessmentModel, assessmentTemperature, assessmentMaxTokens);
    }

    private String chat(String systemPrompt, String userPrompt, String model,
                        double temperature, int maxTokens) throws Exception {
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
}
