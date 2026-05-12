package com.qb.core.dto;

import java.time.Instant;
import java.util.UUID;

public record CategoryDTO(
        UUID id,
        String name,
        String interviewType,
        long questionCount,
        Instant createdAt
) {}
