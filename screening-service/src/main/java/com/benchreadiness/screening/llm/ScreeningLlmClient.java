package com.benchreadiness.screening.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
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
 * Standalone LLM client for screening-service — deliberately not shared with ai-service's LlmClient
 * hierarchy so this new service stays fully isolated from the existing AI infrastructure.
 * Primary provider is Claude or DeepSeek (app.screening.llm-provider); Ollama is the local-dev fallback.
 */
@Component
public class ScreeningLlmClient {

    private static final Logger log = LoggerFactory.getLogger(ScreeningLlmClient.class);

    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    // Which primary provider to use: 'claude' or 'deepseek'. Ollama remains the fallback either way.
    @Value("${app.screening.llm-provider:claude}")
    private String llmProvider;

    @Value("${app.claude.api-key:}")
    private String apiKey;

    @Value("${app.claude.model:claude-sonnet-4-6}")
    private String model;

    @Value("${app.claude.question-temperature:0.5}")
    private double questionTemperature;

    @Value("${app.claude.grading-temperature:0.1}")
    private double gradingTemperature;

    @Value("${app.deepseek.api-key:}")
    private String deepseekApiKey;

    @Value("${app.deepseek.base-url:https://api.deepseek.com}")
    private String deepseekBaseUrl;

    @Value("${app.deepseek.model:deepseek-chat}")
    private String deepseekModel;

    // Fallback used when the primary provider has no key configured — defaults on for local dev,
    // explicitly disabled in docker-compose for deployed environments (which must have a real key).
    @Value("${app.screening.ollama-fallback-enabled:true}")
    private boolean ollamaFallbackEnabled;

    @Value("${app.ollama.base-url:http://127.0.0.1:11434}")
    private String ollamaBaseUrl;

    @Value("${app.ollama.model:qwen2.5:7b}")
    private String ollamaModel;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    private final ObjectMapper objectMapper;

    public ScreeningLlmClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return "deepseek".equals(llmProvider)
                ? deepseekApiKey != null && !deepseekApiKey.isBlank()
                : apiKey != null && !apiKey.isBlank();
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public String generateQuestions(String systemPrompt, String userPrompt) throws Exception {
        return chat(systemPrompt, userPrompt, questionTemperature, 4000);
    }

    /** For calls whose response is larger than usual (e.g. transcribing an existing question paper verbatim). */
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public String generateQuestions(String systemPrompt, String userPrompt, int maxTokens) throws Exception {
        return chat(systemPrompt, userPrompt, questionTemperature, maxTokens);
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public String gradeAnswer(String systemPrompt, String userPrompt) throws Exception {
        return chat(systemPrompt, userPrompt, gradingTemperature, 800);
    }

    private String chat(String systemPrompt, String userPrompt, double temperature, int maxTokens) throws Exception {
        if (!isConfigured()) {
            if (!ollamaFallbackEnabled) {
                throw new IllegalStateException(
                    "No " + llmProvider + " API key configured for screening-service (APP_" + llmProvider.toUpperCase() + "_API_KEY)");
            }
            log.warn("[Screening] No {} API key configured — falling back to local Ollama ({})", llmProvider, ollamaModel);
            return chatOllama(systemPrompt, userPrompt, temperature);
        }
        if ("deepseek".equals(llmProvider)) {
            return chatDeepSeek(systemPrompt, userPrompt, temperature, maxTokens);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("temperature", temperature);
        body.put("system", systemPrompt);
        body.put("messages", List.of(Map.of("role", "user", "content", userPrompt)));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CLAUDE_API_URL))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 429) {
            Thread.sleep(3000);
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }
        if (response.statusCode() != 200) {
            throw new RuntimeException("Claude returned " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String text = root.path("content").get(0).path("text").asText().trim();
        return stripMarkdownFences(text);
    }

    /** DeepSeek — OpenAI-compatible POST {baseUrl}/chat/completions, same shape as ai-service's DeepSeekAiClient. */
    private String chatDeepSeek(String systemPrompt, String userPrompt, double temperature, int maxTokens) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", deepseekModel);
        body.put("max_tokens", maxTokens);
        body.put("temperature", temperature);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));

        String url = (deepseekBaseUrl.endsWith("/") ? deepseekBaseUrl.substring(0, deepseekBaseUrl.length() - 1) : deepseekBaseUrl) + "/chat/completions";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + deepseekApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 429) {
            Thread.sleep(3000);
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }
        if (response.statusCode() != 200) {
            throw new RuntimeException("DeepSeek returned " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String text = root.path("choices").get(0).path("message").path("content").asText().trim();
        return stripMarkdownFences(text);
    }

    /** Local-dev fallback — POST {baseUrl}/api/chat, same shape as ai-service's OllamaAiClient. */
    private String chatOllama(String systemPrompt, String userPrompt, double temperature) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", ollamaModel);
        body.put("stream", false);
        body.put("think", false);
        body.put("options", Map.of("temperature", Math.max(0.0, Math.min(2.0, temperature))));
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));

        String url = (ollamaBaseUrl.endsWith("/") ? ollamaBaseUrl.substring(0, ollamaBaseUrl.length() - 1) : ollamaBaseUrl) + "/api/chat";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(300))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Ollama returned " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String text = root.path("message").path("content").asText("").trim();
        text = text.replaceAll("(?s)<think>.*?</think>", "").trim();
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
}
