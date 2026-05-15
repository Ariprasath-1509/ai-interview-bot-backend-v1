package com.qb.ai.dto;

import java.util.List;

/**
 * Clean structured DTO for Spring AI BeanOutputConverter.
 * Represents the exact shape of the JSON the AI should generate.
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
            List<String> suggestedTags
    ) {}
}
