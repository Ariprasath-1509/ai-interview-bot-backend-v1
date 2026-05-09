package com.qb.core.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Request body for updating a question (admin).
 */
public record QuestionUpdateRequest(
        @NotBlank String text,
        @NotBlank String category,
        List<String> tags
) {}
