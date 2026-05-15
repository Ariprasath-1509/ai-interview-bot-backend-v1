package com.qb.ai.dto;

import java.util.List;
import java.util.UUID;

/**
 * AI parse response — structured sessions with questions, tags, and fuzzy matches.
 * This is what the admin sees on the Review Screen before committing.
 */
public record DigestParseResponse(
        List<ParsedSession> sessions
) {
    public record ParsedSession(
            String candidateName,
            String company,
            String round,
            String date,
            String interviewer,
            List<ParsedQuestion> questions
    ) {}

    public record ParsedQuestion(
            String tempId,
            String text,
            String category,
            List<String> suggestedTags,
            ExistingMatch existingMatch
    ) {}

    public record ExistingMatch(
            UUID id,
            String text,
            double similarity,
            List<String> askedAt
    ) {}
}
