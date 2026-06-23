package com.qb.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qb.ai.dto.DigestAiResponse;
import com.qb.ai.llm.JsonRepairUtil;
import com.qb.ai.llm.PromptTemplates;
import com.qb.config.LlmConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Ollama API integration for local LLM inference.
 */
@Slf4j
@Service
public class OllamaService {

    private final LlmConfig config;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OllamaService(LlmConfig config) {
        this.config = config;
    }

    /**
     * Parse interview text using Ollama.
     * Automatically retries once with a correction prefix if the first response is invalid JSON.
     */
    public DigestAiResponse parse(String rawText, String categoryList,
                                  BeanOutputConverter<DigestAiResponse> converter) {
        try {
            log.info("Calling Ollama API at: {} with model: {}",
                    config.getOllama().getBaseUrl(), config.getOllama().getModel());

            String systemPrompt = PromptTemplates.DIGEST_SYSTEM_PROMPT
                    .replace("{categoryList}", categoryList);

            String userPrompt = PromptTemplates.DIGEST_USER_PROMPT
                    .replace("{jsonSchema}", converter.getFormat())
                    .replace("{rawText}", rawText);

            String fullPrompt = systemPrompt + "\n" + userPrompt;

            String content = callOllama(fullPrompt);

            // Attempt 1
            try {
                DigestAiResponse result = converter.convert(content);
                if (result == null || result.sessions() == null) {
                    throw new IllegalStateException("Converter returned null sessions — model output did not match schema");
                }
                log.info("Ollama parse successful: {} sessions", result.sessions().size());
                return result;
            } catch (Exception firstAttemptEx) {
                log.warn("First parse attempt failed ({}), retrying with correction prefix", firstAttemptEx.getMessage());
            }

            // Attempt 2 — inject correction prefix
            String retryPrompt = fullPrompt +
                    "\n\nIMPORTANT: Your previous response violated JSON formatting rules. " +
                    "Output ONLY corrected valid JSON matching the schema. No explanation. No markdown.";
            String retryContent = callOllama(retryPrompt);

            DigestAiResponse retryResult = converter.convert(retryContent);
            if (retryResult == null || retryResult.sessions() == null) {
                throw new IllegalStateException("Retry also returned null sessions — model output did not match schema");
            }
            log.info("Ollama retry parse successful: {} sessions", retryResult.sessions().size());
            return retryResult;

        } catch (Exception e) {
            log.error("Ollama API failed after retry: {}", e.getMessage(), e);
            throw new RuntimeException("Ollama API failed: " + e.getMessage(), e);
        }
    }

    private String callOllama(String fullPrompt) throws Exception {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", config.getOllama().getTemperature());
        options.put("num_predict", 2048);
        options.put("stop", List.of("```"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getOllama().getModel());
        body.put("prompt", fullPrompt);
        body.put("stream", false);
        body.put("think", false);  // top-level field — disables qwen3 thinking mode
        body.put("options", options);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String url = config.getOllama().getBaseUrl() + "/api/generate";
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                JsonNode.class
        );

        JsonNode root = response.getBody();
        // qwen3 with thinking disabled returns response field directly
        // with thinking enabled it may split into thinking + response fields
        String text = root.path("response").asText("").trim();
        if (text.isBlank()) {
            // fallback: check message.content for chat-style responses
            text = root.path("message").path("content").asText("").trim();
        }
        log.debug("Raw Ollama response (first 500 chars): {}", text.substring(0, Math.min(500, text.length())));
        return JsonRepairUtil.extractJson(text);
    }
}