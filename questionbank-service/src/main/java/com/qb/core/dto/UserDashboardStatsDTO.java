package com.qb.core.dto;

import java.time.LocalDate;

public record UserDashboardStatsDTO(
        long totalSessions,
        long totalCompanies,
        LocalDate lastInterviewDate
) {}
