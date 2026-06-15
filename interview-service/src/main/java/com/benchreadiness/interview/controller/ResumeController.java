package com.benchreadiness.interview.controller;

import com.benchreadiness.interview.client.AuthServiceClient;
import com.benchreadiness.interview.service.ResumeParsingService;
import com.benchreadiness.interview.service.ResumeStorageService;
import com.benchreadiness.interview.service.ResumeSummaryService;
import com.benchreadiness.interview.service.ResumeHistoryService;
import com.benchreadiness.interview.service.BulkResumeProcessingService;
import com.benchreadiness.interview.service.ResumeAnalyticsService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/resumes")
public class ResumeController {

    @GetMapping("/test")
    public ResponseEntity<?> test() {
        return ResponseEntity.ok(Map.of("status", "Resume service is working", "timestamp", Instant.now().toString()));
    }

    private final ResumeStorageService storageService;
    private final ResumeParsingService parsingService;
    private final ResumeSummaryService summaryService;
    private final AuthServiceClient authServiceClient;
    private final ResumeHistoryService historyService;
    private final BulkResumeProcessingService bulkProcessingService;
    private final ResumeAnalyticsService analyticsService;

    public ResumeController(ResumeStorageService storageService,
                           ResumeParsingService parsingService,
                           ResumeSummaryService summaryService,
                           AuthServiceClient authServiceClient,
                           ResumeHistoryService historyService,
                           BulkResumeProcessingService bulkProcessingService,
                           ResumeAnalyticsService analyticsService) {
        this.storageService = storageService;
        this.parsingService = parsingService;
        this.summaryService = summaryService;
        this.authServiceClient = authServiceClient;
        this.historyService = historyService;
        this.bulkProcessingService = bulkProcessingService;
        this.analyticsService = analyticsService;
    }

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('CANDIDATE', 'ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<?> uploadResume(@RequestParam("resume") MultipartFile file,
                                         @RequestParam(value = "candidateId", required = false) String candidateId,
                                         @RequestHeader("X-User-Id") String userId,
                                         @RequestHeader("X-User-Role") String userRole) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "No file provided"));
            }
            parsingService.validateFileType(file);
            if (file.getSize() > 5 * 1024 * 1024) {
                return ResponseEntity.badRequest().body(Map.of("error", "File size must be under 5MB"));
            }

            String targetUserId = userId;
            if (!"CANDIDATE".equals(userRole)) {
                if (candidateId == null || candidateId.isBlank()) {
                    return ResponseEntity.badRequest().body(Map.of("error", "candidateId is required for admin upload"));
                }
                targetUserId = candidateId;
            }

            Map<String, Object> candidate = authServiceClient.getUserById(targetUserId);
            String candidateName = (String) candidate.getOrDefault("name", "Unknown");

            String filePath = storageService.storeResume(targetUserId, file);
            ResumeParsingService.ResumeParseResult parseResult = parsingService.parseResume(file);

            String extractedText = "";
            String summary = "";

            if (parseResult.isSuccess()) {
                extractedText = parseResult.getExtractedText();
                ResumeSummaryService.ResumeSummaryResult summaryResult =
                    summaryService.processResumeSummary(extractedText, candidateName);
                summary = summaryResult.getSummary();
            } else {
                summary = "Resume uploaded successfully but text extraction failed: " + parseResult.getErrorMessage();
            }

            Map<String, Object> updateRequest = new HashMap<>();
            updateRequest.put("resumeFilename", file.getOriginalFilename());
            updateRequest.put("resumeFilePath", filePath);
            updateRequest.put("resumeParsedText", extractedText);
            updateRequest.put("resumeSummary", summary);
            updateRequest.put("resumeUploadedAt", Instant.now().toString());
            updateRequest.put("resumeUpdatedAt", Instant.now().toString());

            try {
                authServiceClient.updateCandidateResume(targetUserId, updateRequest);
            } catch (Exception e) {
                System.err.println("Failed to update candidate profile: " + e.getMessage());
            }

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Resume uploaded and processed successfully",
                "filename", file.getOriginalFilename(),
                "size", file.getSize(),
                "textExtracted", parseResult.isSuccess(),
                "summary", summary
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Resume upload failed: " + e.getMessage()));
        }
    }

    @GetMapping("/{candidateId}")
    @PreAuthorize("hasAnyRole('CANDIDATE', 'ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<?> downloadResume(@PathVariable String candidateId,
                                           @RequestHeader("X-User-Id") String userId,
                                           @RequestHeader("X-User-Role") String userRole) {
        try {
            if ("CANDIDATE".equals(userRole) && !candidateId.equals(userId)) {
                return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
            }

            Map<String, Object> candidate = authServiceClient.getUserById(candidateId);
            String resumeFilePath = (String) candidate.get("resumeFilePath");
            String resumeFilename = (String) candidate.get("resumeFilename");

            if (resumeFilePath == null || !storageService.resumeExists(resumeFilePath)) {
                return ResponseEntity.notFound().build();
            }

            byte[] resumeContent = storageService.getResumeContent(resumeFilePath);
            String contentType = "application/octet-stream";
            if (resumeFilename != null) {
                if (resumeFilename.toLowerCase().endsWith(".pdf")) contentType = "application/pdf";
                else if (resumeFilename.toLowerCase().endsWith(".doc")) contentType = "application/msword";
                else if (resumeFilename.toLowerCase().endsWith(".docx")) contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            }

            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resumeFilename + "\"")
                .body(resumeContent);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to download resume: " + e.getMessage()));
        }
    }

    @PostMapping("/{candidateId}/summary")
    @PreAuthorize("hasAnyRole('CANDIDATE', 'ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<?> updateResumeSummary(@PathVariable String candidateId,
                                                 @RequestBody Map<String, String> request,
                                                 @RequestHeader("X-User-Id") String userId,
                                                 @RequestHeader("X-User-Role") String userRole) {
        try {
            if ("CANDIDATE".equals(userRole) && !candidateId.equals(userId)) {
                return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
            }
            String newSummary = request.get("summary");
            if (newSummary == null || newSummary.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Summary cannot be empty"));
            }
            Map<String, Object> updateRequest = new HashMap<>();
            updateRequest.put("resumeSummary", newSummary.trim());
            updateRequest.put("resumeUpdatedAt", Instant.now().toString());
            authServiceClient.updateCandidateResume(candidateId, updateRequest);
            return ResponseEntity.ok(Map.of("success", true, "message", "Resume summary updated successfully", "summary", newSummary.trim()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to update resume summary: " + e.getMessage()));
        }
    }

    @GetMapping("/{candidateId}/history")
    @PreAuthorize("hasAnyRole('CANDIDATE', 'ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<?> getResumeHistory(@PathVariable String candidateId,
                                             @RequestHeader("X-User-Id") String userId,
                                             @RequestHeader("X-User-Role") String userRole) {
        try {
            if ("CANDIDATE".equals(userRole) && !candidateId.equals(userId)) {
                return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
            }
            ResumeHistoryService.ResumeHistorySummary history = historyService.getHistorySummary(candidateId);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to get resume history: " + e.getMessage()));
        }
    }

    @PostMapping("/bulk-process")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> bulkProcessResumes(@RequestHeader("X-User-Id") String userId,
                                               @RequestHeader("X-User-Role") String userRole) {
        try {
            BulkResumeProcessingService.BulkProcessingResult result =
                bulkProcessingService.processCandidatesWithoutSummaries(userId, userRole);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to start bulk processing: " + e.getMessage()));
        }
    }

    @GetMapping("/processing-stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<?> getProcessingStats(@RequestHeader("X-User-Id") String userId,
                                               @RequestHeader("X-User-Role") String userRole) {
        try {
            BulkResumeProcessingService.ResumeProcessingStats stats =
                bulkProcessingService.getProcessingStats(userId, userRole);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to get processing stats: " + e.getMessage()));
        }
    }

    @GetMapping("/analytics")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<?> getResumeAnalytics(@RequestHeader("X-User-Id") String userId,
                                               @RequestHeader("X-User-Role") String userRole) {
        try {
            ResumeAnalyticsService.ResumeAnalytics analytics =
                analyticsService.getResumeAnalytics(userId, userRole);
            return ResponseEntity.ok(analytics);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to get resume analytics: " + e.getMessage()));
        }
    }

    @GetMapping("/analytics/trends")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<?> getUploadTrends(@RequestParam(defaultValue = "30") int days,
                                            @RequestHeader("X-User-Id") String userId,
                                            @RequestHeader("X-User-Role") String userRole) {
        try {
            ResumeAnalyticsService.UploadTrends trends =
                analyticsService.getUploadTrends(userId, userRole, days);
            return ResponseEntity.ok(trends);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to get upload trends: " + e.getMessage()));
        }
    }
}
