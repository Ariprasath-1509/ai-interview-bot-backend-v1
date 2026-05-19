package com.qb.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** * Configuration for LLM providers (Claude, Ollama). * Binds to app.llm, app.claude, app.ollama properties. */
@Component
@ConfigurationProperties(prefix = "app")
@Data
public class LlmConfig {

    private Llm llm = new Llm();
    private Claude claude = new Claude();
    private Ollama ollama = new Ollama();

    @Data
    public static class Llm {
        private String provider = "claude"; // Default to claude
    }

    @Data
    public static class Claude {
        private String apiKey;
        private String model;
    }

    @Data
    public static class Ollama {
        private String baseUrl;
        private String model;
        private Double temperature = 0.3;
        private Integer timeoutSeconds = 600;
    }
}