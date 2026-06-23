package com.qb.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qb.ai.dto.DigestAiResponse;
import com.qb.ai.llm.PromptTemplates;
import com.qb.config.LlmConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Claude API integration via Anthropic REST API.
 */
@Slf4j
@Service
public class ClaudeService {

    private final LlmConfig config;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ClaudeService(LlmConfig config) {
        this.config = config;
    }

    /**
     * Parse interview text using Claude.
     */
    public DigestAiResponse parse(String rawText, String categoryList,
                                  BeanOutputConverter<DigestAiResponse> converter) {
        String apiKey = config.getClaude().getApiKey();
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("sk-ant-your")) {
            throw new RuntimeException("Claude API key not configured");
        }
        try {
            log.info("Calling Claude API with model: {}", config.getClaude().getModel());

            String systemPrompt = PromptTemplates.DIGEST_SYSTEM_PROMPT.replace("{categoryList}", categoryList);
            String fullPrompt = systemPrompt + "\n\nHere is the interview text to parse. Respond ONLY with valid JSON matching the required schema:\n" + rawText
                    + "\n\n" + converter.getFormat();

            Map<String, Object> body = Map.of(
                    "model", config.getClaude().getModel(),
                    "max_tokens", 8000,
                    "messages", List.of(Map.of("role", "user", "content", fullPrompt))
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", config.getClaude().getApiKey());
            headers.set("anthropic-version", "2023-06-01");

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    "https://api.anthropic.com/v1/messages",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    JsonNode.class
            );

            String content = response.getBody().path("content").get(0).path("text").asText();
            // Strip markdown fences if present
            content = content.replaceAll("(?s)```json\\s*", "").replaceAll("```", "").trim();

            DigestAiResponse result = converter.convert(content);
            log.info("Claude parse successful: {} sessions", result.sessions().size());
            return result;
        } catch (Exception e) {
            log.error("Claude API failed: {}", e.getMessage(), e);
            throw new RuntimeException("Claude API failed: " + e.getMessage(), e);
        }
    }
}