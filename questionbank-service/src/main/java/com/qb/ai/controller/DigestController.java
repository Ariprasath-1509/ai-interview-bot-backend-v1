package com.qb.ai.controller;

import com.qb.ai.dto.*;
import com.qb.ai.service.DigestService;
import com.qb.ai.service.DocxExtractorService;
import com.qb.core.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Admin-only digest endpoints.
 * POST /api/digest/parse   → AI parses raw text, returns preview
 * POST /api/digest/upload  → Accepts .docx, extracts text, chunks and parses
 * POST /api/digest/commit  → Saves admin-approved data to DB
 */
@Slf4j
@RestController
@RequestMapping("/api/digest")
@RequiredArgsConstructor
public class DigestController {

    private final DigestService digestService;
    private final DocxExtractorService docxExtractorService;

    @PostMapping("/parse")
    public ResponseEntity<ApiResponse<DigestParseResponse>> parse(
            @Valid @RequestBody DigestParseRequest request
    ) {
        DigestParseResponse response = digestService.parse(request.rawText());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, String>>> extract(
            @RequestParam("file") MultipartFile file
    ) {
        try {
            String text = docxExtractorService.extractText(file);
            return ResponseEntity.ok(ApiResponse.ok(Map.of("text", text)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Digest extract error", e);
            return ResponseEntity.status(500).body(ApiResponse.error("Text extraction failed: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DigestParseResponse>> upload(
            @RequestParam("file") MultipartFile file
    ) {
        try {
            String text = docxExtractorService.extractText(file);
            DigestParseResponse response = digestService.parseChunked(text);
            return ResponseEntity.ok(ApiResponse.ok(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Digest upload error", e);
            return ResponseEntity.status(500).body(ApiResponse.error("Upload processing failed: " + e.getMessage()));
        }
    }

    @PostMapping("/commit")
    public ResponseEntity<ApiResponse<DigestCommitResponse>> commit(
            @Valid @RequestBody DigestCommitRequest request
    ) {
        DigestCommitResponse response = digestService.commit(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
