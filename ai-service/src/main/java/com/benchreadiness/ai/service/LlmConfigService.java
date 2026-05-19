package com.benchreadiness.ai.service;

import com.benchreadiness.ai.client.LlmConfigClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class LlmConfigService {

    private static final Logger log = LoggerFactory.getLogger(LlmConfigService.class);
    private final LlmConfigClient configClient;

    public LlmConfigService(LlmConfigClient configClient) {
        this.configClient = configClient;
    }

    @Cacheable(value = "llmConfig", unless = "#result == null")
    public LlmConfigClient.LlmConfigResponse getConfig() {
        try {
            LlmConfigClient.LlmConfigResponse config = configClient.getConfig();
            log.info("[LLM_CONFIG] Fetched configuration: provider={}", config.getProvider());
            return config;
        } catch (Exception e) {
            log.error("[LLM_CONFIG] Failed to fetch configuration, using defaults", e);
            return getDefaultConfig();
        }
    }

    public String getModelForOperation(String operation) {
        LlmConfigClient.LlmConfigResponse config = getConfig();
        String provider = config.getProvider();
        
        Map<String, String> models = "CLAUDE".equals(provider) 
                ? config.getClaudeModels() 
                : config.getOllamaModels();
        
        String model = models.get(operation.toLowerCase());
        log.debug("[LLM_CONFIG] Operation={}, Provider={}, Model={}", operation, provider, model);
        return model;
    }

    public String getProvider() {
        return getConfig().getProvider();
    }

    private LlmConfigClient.LlmConfigResponse getDefaultConfig() {
        LlmConfigClient.LlmConfigResponse config = new LlmConfigClient.LlmConfigResponse();
        config.setProvider("CLAUDE");
        config.setClaudeModels(Map.of(
                "question", "claude-haiku-4-5",
                "assessment", "claude-sonnet-4-5",
                "rubric", "claude-haiku-4-5",
                "matching", "claude-sonnet-4-5"
        ));
        config.setOllamaModels(Map.of(
                "question", "qwen2.5:7b",
                "assessment", "qwen2.5:32b",
                "rubric", "qwen2.5:14b",
                "matching", "qwen2.5:32b"
        ));
        return config;
    }
}
