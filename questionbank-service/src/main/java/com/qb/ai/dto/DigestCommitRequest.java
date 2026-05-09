package com.qb.ai.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Admin-approved data to commit to the database.
 * Sent after the admin reviews the parse results on the Review Screen.
 */
public record DigestCommitRequest(
        @NotEmpty List<CommitSession> sessions
) {
    public record CommitSession(
            @NotNull String candidateName,
            @NotNull String companyName,
            @NotNull String round,
            String date,
            String interviewerName,
            UUID candidateId,
            @NotEmpty List<CommitQuestion> questions
    ) {}

    public record CommitQuestion(
            UUID existingQuestionId,
            @NotNull String text,
            @NotNull String category,
            List<String> tags
    ) {}
}
