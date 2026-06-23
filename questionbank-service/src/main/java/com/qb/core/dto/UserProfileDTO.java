package com.qb.core.dto;

import java.time.Instant;
import java.util.UUID;

public record UserProfileDTO(
        UUID id,
        String name,
        String email,
        String phone,
        boolean isAdmin,
        long sessionCount,
        Instant createdAt
) {}
