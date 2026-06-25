package com.benchreadiness.ai.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime-mutable routing table for the hybrid LLM setup.
 * Loaded from application.yml on startup; admin can update via API without restart.
 */
@Component
public class LlmProviderSettings {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LlmProviderSettings.class);

    @Value("${app.llm.provider:claude}")
    private String startupProvider;

    // Per-operation provider: "claude" or "ollama"
    private volatile String questionProvider;
    private volatile String rubricProvider;
    private volatile String assessmentProvider;
    private volatile String matchingProvider;

    @PostConstruct
    public void init() {
        if ("hybrid".equals(startupProvider)) {
            // Hybrid mode: default all operations to Claude (override per-op via admin UI if needed)
            questionProvider   = "claude";
            rubricProvider     = "claude";
            assessmentProvider = "claude";
            matchingProvider   = "claude";
        } else {
            // Single-provider mode: route everything to the configured provider
            questionProvider   = startupProvider;
            rubricProvider     = startupProvider;
            assessmentProvider = startupProvider;
            matchingProvider   = startupProvider;
        }
        log.info("[LlmProviderSettings] Initialized — question={}, rubric={}, assessment={}, matching={}",
                questionProvider, rubricProvider, assessmentProvider, matchingProvider);
    }

    public String getStartupProvider() { return startupProvider; }

    /** When true, HybridLlmClient may fall back to Ollama if Claude is unavailable. */
    public boolean ollamaFallbackEnabled() {
        return "hybrid".equals(startupProvider) || "ollama".equals(startupProvider);
    }

    public String getQuestionProvider()   { return questionProvider; }
    public String getRubricProvider()     { return rubricProvider; }
    public String getAssessmentProvider() { return assessmentProvider; }
    public String getMatchingProvider()   { return matchingProvider; }

    public void setQuestionProvider(String p)   { this.questionProvider   = validated(p); }
    public void setRubricProvider(String p)     { this.rubricProvider     = validated(p); }
    public void setAssessmentProvider(String p) { this.assessmentProvider = validated(p); }
    public void setMatchingProvider(String p)   { this.matchingProvider   = validated(p); }

    private String validated(String p) {
        if (!"claude".equals(p) && !"ollama".equals(p)) {
            throw new IllegalArgumentException("Provider must be 'claude' or 'ollama', got: " + p);
        }
        return p;
    }

    public Map<String, String> toMap() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("questionProvider",   questionProvider);
        m.put("rubricProvider",     rubricProvider);
        m.put("assessmentProvider", assessmentProvider);
        m.put("matchingProvider",   matchingProvider);
        return m;
    }

    public void applyMap(Map<String, String> m) {
        if (m.containsKey("questionProvider"))   setQuestionProvider(m.get("questionProvider"));
        if (m.containsKey("rubricProvider"))     setRubricProvider(m.get("rubricProvider"));
        if (m.containsKey("assessmentProvider")) setAssessmentProvider(m.get("assessmentProvider"));
        if (m.containsKey("matchingProvider"))   setMatchingProvider(m.get("matchingProvider"));
        log.info("[LlmProviderSettings] Updated — question={}, rubric={}, assessment={}, matching={}",
                questionProvider, rubricProvider, assessmentProvider, matchingProvider);
    }
}
