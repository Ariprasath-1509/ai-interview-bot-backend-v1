package com.qb.ai.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to parse raw interview text via AI.
 */
public record DigestParseRequest(
        @NotBlank String rawText
) {}
