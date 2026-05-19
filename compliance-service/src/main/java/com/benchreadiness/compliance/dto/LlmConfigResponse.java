package com.benchreadiness.compliance.dto;

import java.time.Instant;
import java.util.Map;

public class LlmConfigResponse {
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
