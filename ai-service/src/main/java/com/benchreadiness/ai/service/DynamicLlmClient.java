package com.benchreadiness.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class DynamicLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(DynamicLlmClient.class);

    private final LlmConfigService configService;
    private final ClaudeAiClient claudeClient;
    private final OllamaAiClient ollamaClient;

    public DynamicLlmClient(LlmConfigService configService, ClaudeAiClient claudeClient, OllamaAiClient ollamaClient) {
        this.configService = configService;
        this.claudeClient = claudeClient;
        this.ollamaClient = ollamaClient;
    }

    private LlmClient getActiveClient() {
        String provider = configService.getProvider();
        return "OLLAMA".equals(provider) ? ollamaClient : claudeClient;
    }

    @Override
    public boolean isConfigured() {
        return getActiveClient().isConfigured();
    }

    @Override
    public String chatQuestion(String systemPrompt, String userPrompt) throws Exception {
        return getActiveClient().chatQuestion(systemPrompt, userPrompt);
    }

    @Override
    public String chatQuestionWithSlotAndTracking(String systemPrompt, String userPrompt, int slot, String interviewId, String userId) throws Exception {
        return getActiveClient().chatQuestionWithSlotAndTracking(systemPrompt, userPrompt, slot, interviewId, userId);
    }

    @Override
    public String chatAssessmentWithTracking(String systemPrompt, String userPrompt, String interviewId, String userId) throws Exception {
        return getActiveClient().chatAssessmentWithTracking(systemPrompt, userPrompt, interviewId, userId);
    }

    @Override
    public String chatRubricWithTracking(String systemPrompt, String userPrompt, String interviewId, String userId) throws Exception {
        return getActiveClient().chatRubricWithTracking(systemPrompt, userPrompt, interviewId, userId);
    }

    @Override
    public String chatMatching(String systemPrompt, String userPrompt) throws Exception {
        return getActiveClient().chatMatching(systemPrompt, userPrompt);
    }
}
