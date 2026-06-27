package com.benchreadiness.ai.dto;

import java.util.List;

/**
 * Structured response from the LLM for interview text parsing.
 * Mirrors the questionbank-service DigestAiResponse shape so both services
 * share the same JSON contract.
 */
public record DigestAiResponse(List<AiSession> sessions) {

    public record AiSession(
            String candidateName,
            String company,
            String round,
            String date,
            String interviewer,
            List<AiQuestion> questions
    ) {}

    public record AiQuestion(
            String text,
            String category,
            List<String> suggestedTags,
            Double confidence
    ) {
        public AiQuestion {
            if (confidence == null) confidence = 1.0;
        }
    }
}
