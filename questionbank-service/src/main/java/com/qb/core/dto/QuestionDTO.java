package com.qb.core.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record QuestionDTO(
        UUID id,
        String text,
        UUID categoryId,
        String category,
        String interviewType,
        int occurrenceCount,
        List<String> tags,
        List<String> askedByCompanies,
        List<SessionInfo> sessions,
        String relevancyLabel,
        Instant createdAt,
        Instant updatedAt
) {
    public record SessionInfo(
            String companyName,
            String round,
            LocalDate interviewDate
    ) {}
}
