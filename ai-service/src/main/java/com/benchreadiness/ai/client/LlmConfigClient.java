package com.benchreadiness.ai.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@FeignClient(name = "compliance-service", contextId = "llmConfig")
public interface LlmConfigClient {

    @GetMapping("/admin/llm/config")
    LlmConfigResponse getConfig();

    class LlmConfigResponse {
        private String provider;
        private Map<String, String> claudeModels;
        private Map<String, String> ollamaModels;
        private String updatedBy;
        private Instant updatedAt;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }

        public Map<String, String> getClaudeModels() { return claudeModels; }
        public void setClaudeModels(Map<String, String> claudeModels) { this.claudeModels = claudeModels; }

        public Map<String, String> getOllamaModels() { return ollamaModels; }
        public void setOllamaModels(Map<String, String> ollamaModels) { this.ollamaModels = ollamaModels; }

        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

        public Instant getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    }
}
