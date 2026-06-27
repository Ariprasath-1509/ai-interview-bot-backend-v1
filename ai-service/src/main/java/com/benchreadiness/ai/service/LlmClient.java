package com.benchreadiness.ai.service;

/**
 * Provider-agnostic LLM client. Switch implementation via config.
 */
public interface LlmClient {
    /** True when the provider is configured well enough to attempt calls. */
    boolean isConfigured();

    /** Short-form question generation. */
    String chatQuestion(String systemPrompt, String userPrompt) throws Exception;

    /** Question generation with slot-based model selection and optional token tracking. */
    String chatQuestionWithSlotAndTracking(String systemPrompt, String userPrompt, int slot, String interviewId, String userId) throws Exception;

    /** Assessment / long-form structured output, with optional token tracking. */
    String chatAssessmentWithTracking(String systemPrompt, String userPrompt, String interviewId, String userId) throws Exception;

    /** Rubric generation, with optional token tracking. */
    String chatRubricWithTracking(String systemPrompt, String userPrompt, String interviewId, String userId) throws Exception;

    /** Candidate matching. */
    String chatMatching(String systemPrompt, String userPrompt) throws Exception;

    /** Digest parse — large structured JSON output from raw interview text. */
    String chatDigest(String systemPrompt, String userPrompt) throws Exception;
}

