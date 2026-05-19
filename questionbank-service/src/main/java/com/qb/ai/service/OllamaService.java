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

import java.util.Map;

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
     */
    public DigestAiResponse parse(String rawText, String categoryList,
                                  BeanOutputConverter<DigestAiResponse> converter) {
        try {
            log.info("Calling Ollama API at: {} with model: {}",
                    config.getOllama().getBaseUrl(), config.getOllama().getModel());

            String systemPrompt = PromptTemplates.DIGEST_SYSTEM_PROMPT.replace("{categoryList}", categoryList);
            String fullPrompt = systemPrompt + "\n\nHere is the interview text to parse. Respond ONLY with valid JSON matching the required schema:\n" + rawText
                    + "\n\n" + converter.getFormat();

            Map<String, Object> body = Map.of(
                    "model", config.getOllama().getModel(),
                    "prompt", fullPrompt,
                    "stream", false,
                    "temperature", config.getOllama().getTemperature()
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String url = config.getOllama().getBaseUrl() + "/api/generate";
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    JsonNode.class
            );

            String content = response.getBody().path("response").asText();
            content = content.replaceAll("(?s)```json\\s*", "").replaceAll("```", "").trim();

            DigestAiResponse result = converter.convert(content);
            log.info("Ollama parse successful: {} sessions", result.sessions().size());
            return result;
        } catch (Exception e) {
            log.error("Ollama API failed: {}", e.getMessage(), e);
            throw new RuntimeException("Ollama API failed: " + e.getMessage(), e);
        }
    }
}