package com.benchreadiness.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record DigestParseRequest(
        @NotBlank String rawText,
        String categoryList
) {}
