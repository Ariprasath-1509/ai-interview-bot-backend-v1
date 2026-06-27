package com.qb.client;

import com.qb.config.FeignAuthConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Feign client for the ai-service.
 * Routes all LLM calls through the centralized ai-service instead of calling
 * Claude/Ollama directly from the questionbank-service.
 */
@FeignClient(name = "ai-service", path = "/ai", configuration = FeignAuthConfig.class)
public interface AiServiceClient {

    /**
     * Parse raw interview text via the ai-service LLM.
     * Body: { "rawText": "...", "categoryList": "Java, DSA, ..." }
     * Returns: { "success": true, "data": { "sessions": [...] } }
     */
    @PostMapping("/digest-parse")
    Map<String, Object> digestParse(@RequestBody Map<String, String> body);
}
