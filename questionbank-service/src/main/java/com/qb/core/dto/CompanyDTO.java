package com.qb.core.dto;

import java.time.Instant;
import java.util.UUID;

public record CompanyDTO(
        UUID id,
        String name,
        String slug,
        long questionCount,
        Instant createdAt
) {}
