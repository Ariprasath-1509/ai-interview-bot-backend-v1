package com.qb.core.dto;

import java.time.Instant;
import java.util.Map;

public record AdminStatsDTO(
        long totalQuestions,
        long totalCompanies,
        long totalSessions,
        long totalCandidates,
        Map<String, Long> questionsByImportance,
        Instant lastDigestDate
) {}
