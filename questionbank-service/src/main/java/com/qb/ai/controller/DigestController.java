package com.qb.ai.controller;

import com.qb.ai.dto.*;
import com.qb.ai.service.DigestService;
import com.qb.core.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-only digest endpoints.
 * POST /api/digest/parse  → AI parses raw text, returns preview
 * POST /api/digest/commit → Saves admin-approved data to DB
 */
@RestController
@RequestMapping("/api/digest")
@RequiredArgsConstructor
public class DigestController {

    private final DigestService digestService;

    @PostMapping("/parse")
    public ResponseEntity<ApiResponse<DigestParseResponse>> parse(
            @Valid @RequestBody DigestParseRequest request
    ) {
        DigestParseResponse response = digestService.parse(request.rawText());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/commit")
    public ResponseEntity<ApiResponse<DigestCommitResponse>> commit(
            @Valid @RequestBody DigestCommitRequest request
    ) {
        DigestCommitResponse response = digestService.commit(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
