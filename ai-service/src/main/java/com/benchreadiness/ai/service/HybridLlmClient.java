package com.benchreadiness.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Primary LlmClient that routes each operation to Claude or Ollama
 * based on the runtime-mutable LlmProviderSettings.
 *
 * Routing defaults (can be changed by admin via /ai/admin/llm-settings):
 *   question  → Ollama  (fast, no data egress)
 *   rubric    → Ollama  (fast, no data egress)
 *   assessment→ Claude  (accuracy matters — 4-stage scoring pipeline)
 *   matching  → Claude  (nuanced candidate ranking)
 */
@Service
@Primary
public class HybridLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(HybridLlmClient.class);

    private final ClaudeAiClient claude;
    private final OllamaAiClient ollama;
    private final LlmProviderSettings settings;

    public HybridLlmClient(ClaudeAiClient claude, OllamaAiClient ollama, LlmProviderSettings settings) {
        this.claude   = claude;
        this.ollama   = ollama;
        this.settings = settings;
    }

    @Override
    public boolean isConfigured() {
        return claude.isConfigured() || ollama.isConfigured();
    }

    @Override
    public String chatQuestion(String systemPrompt, String userPrompt) throws Exception {
        return resolve(settings.getQuestionProvider()).chatQuestion(systemPrompt, userPrompt);
    }

    @Override
    public String chatQuestionWithSlotAndTracking(String systemPrompt, String userPrompt,
                                                   int slot, String interviewId, String userId) throws Exception {
        log.debug("[Hybrid] chatQuestion → {}", settings.getQuestionProvider());
        return resolve(settings.getQuestionProvider())
                .chatQuestionWithSlotAndTracking(systemPrompt, userPrompt, slot, interviewId, userId);
    }

    @Override
    public String chatAssessmentWithTracking(String systemPrompt, String userPrompt,
                                              String interviewId, String userId) throws Exception {
        log.debug("[Hybrid] chatAssessment → {}", settings.getAssessmentProvider());
        return resolve(settings.getAssessmentProvider())
                .chatAssessmentWithTracking(systemPrompt, userPrompt, interviewId, userId);
    }

    @Override
    public String chatRubricWithTracking(String systemPrompt, String userPrompt,
                                          String interviewId, String userId) throws Exception {
        log.debug("[Hybrid] chatRubric → {}", settings.getRubricProvider());
        return resolve(settings.getRubricProvider())
                .chatRubricWithTracking(systemPrompt, userPrompt, interviewId, userId);
    }

    @Override
    public String chatMatching(String systemPrompt, String userPrompt) throws Exception {
        log.debug("[Hybrid] chatMatching → {}", settings.getMatchingProvider());
        return resolve(settings.getMatchingProvider()).chatMatching(systemPrompt, userPrompt);
    }

    /**
     * Resolve the concrete client for the requested provider.
     * Falls back to whichever client is configured if the preferred one isn't.
     */
    private LlmClient resolve(String providerName) {
        if ("claude".equals(providerName)) {
            if (claude.isConfigured()) return claude;
            log.warn("[Hybrid] Claude not configured — falling back to Ollama");
            return ollama;
        }
        if ("ollama".equals(providerName)) {
            if (ollama.isConfigured()) return ollama;
            log.warn("[Hybrid] Ollama not configured — falling back to Claude");
            return claude;
        }
        log.warn("[Hybrid] Unknown provider '{}' — defaulting to Ollama", providerName);
        return ollama.isConfigured() ? ollama : claude;
    }

    public LlmProviderSettings getSettings() {
        return settings;
    }

    public boolean isClaudeConfigured() { return claude.isConfigured(); }
    public boolean isOllamaConfigured() { return ollama.isConfigured(); }
}
