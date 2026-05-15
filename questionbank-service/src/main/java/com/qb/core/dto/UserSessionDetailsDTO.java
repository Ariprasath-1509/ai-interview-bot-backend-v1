package com.qb.core.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record UserSessionDetailsDTO(
        UUID id,
        String companyName,
        String companySlug,
        String round,
        LocalDate interviewDate,
        String interviewerName,
        List<QuestionSummary> questions
) {
    public record QuestionSummary(
            UUID id,
            String text,
            String category,
            String relevancyLabel,
            List<String> tags
    ) {}
}
