package com.qb.core.dto;

import java.time.LocalDate;
import java.util.UUID;

public record SessionDTO(
        UUID id,
        String candidateName,
        UUID candidateId,
        String companyName,
        String companySlug,
        String round,
        LocalDate interviewDate,
        String interviewerName
) {}
