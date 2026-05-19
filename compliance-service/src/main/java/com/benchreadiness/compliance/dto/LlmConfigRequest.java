package com.benchreadiness.compliance.dto;

import java.util.Map;

public class LlmConfigRequest {
    private String provider;
    private Map<String, String> claudeModels;
    private Map<String, String> ollamaModels;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public Map<String, String> getClaudeModels() { return claudeModels; }
    public void setClaudeModels(Map<String, String> claudeModels) { this.claudeModels = claudeModels; }

    public Map<String, String> getOllamaModels() { return ollamaModels; }
    public void setOllamaModels(Map<String, String> ollamaModels) { this.ollamaModels = ollamaModels; }
}
