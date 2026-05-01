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
        System.out.println("=== TEST ENDPOINT CALLED ===");
        return ResponseEntity.ok(Map.of("status", "Resume service is working", "timestamp", java.time.Instant.now().toString()));
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
    public ResponseEntity<?> uploadResume(@RequestParam("resume") MultipartFile file,
                                         @RequestHeader("X-User-Id") String userId,
                                         @RequestHeader("X-User-Role") String userRole) {
        System.out.println("=== RESUME UPLOAD STARTED ===");
        System.out.println("UserId: " + userId);
        System.out.println("UserRole: " + userRole);
        System.out.println("File: " + (file != null ? file.getOriginalFilename() : "null"));
        
        try {
            if (!"CANDIDATE".equals(userRole)) {
                return ResponseEntity.status(403).body(Map.of("error", "Only candidates can upload resumes"));
            }
            
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "No file provided"));
            }
            
            parsingService.validateFileType(file);
            if (file.getSize() > 5 * 1024 * 1024) {
                return ResponseEntity.badRequest().body(Map.of("error", "File size must be under 5MB"));
            }
            
            Map<String, Object> candidate = authServiceClient.getUserById(userId);
            String candidateName = (String) candidate.getOrDefault("name", "Unknown");
            System.out.println("Retrieved candidate: " + candidateName + " for userId: " + userId);
            
            String filePath = storageService.storeResume(userId, file);
            System.out.println("Stored resume at path: " + filePath);
            
            ResumeParsingService.ResumeParseResult parseResult = parsingService.parseResume(file);
            System.out.println("Parse result - success: " + parseResult.isSuccess());
            
            String extractedText = "";
            String summary = "";
            
            if (parseResult.isSuccess()) {
                extractedText = parseResult.getExtractedText();
                System.out.println("Extracted text length: " + extractedText.length());
                
                ResumeSummaryService.ResumeSummaryResult summaryResult = 
                    summaryService.processResumeSummary(extractedText, candidateName);
                summary = summaryResult.getSummary();
                System.out.println("Generated summary length: " + summary.length());
            } else {
                summary = "Resume uploaded successfully but text extraction failed: " + parseResult.getErrorMessage();
                System.out.println("Text extraction failed: " + parseResult.getErrorMessage());
            }
            
            Map<String, Object> updateRequest = new HashMap<>();
            updateRequest.put("resumeFilename", file.getOriginalFilename());
            updateRequest.put("resumeFilePath", filePath);
            updateRequest.put("resumeParsedText", extractedText);
            updateRequest.put("resumeSummary", summary);
            updateRequest.put("resumeUploadedAt", Instant.now().toString());
            updateRequest.put("resumeUpdatedAt", Instant.now().toString());
            
            try {
                System.out.println("Attempting to update candidate profile for userId: " + userId);
                System.out.println("Update request data: " + updateRequest);
                System.out.println("Headers - userId: " + userId + ", userRole: " + userRole);
                
                Map<String, Object> updateResult = authServiceClient.updateCandidateResume(userId, updateRequest);
                System.out.println("Successfully updated candidate profile: " + updateResult);
            } catch (Exception e) {
                System.err.println("Failed to update candidate profile: " + e.getMessage());
                System.err.println("Exception type: " + e.getClass().getSimpleName());
                e.printStackTrace();
                // Still return success for the upload, but log the database update failure
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
            System.err.println("=== RESUME UPLOAD ERROR ===");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "error", "Resume upload failed: " + e.getMessage()
            ));
        }
    }
    
    @GetMapping("/{candidateId}")
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
                if (resumeFilename.toLowerCase().endsWith(".pdf")) {
                    contentType = "application/pdf";
                } else if (resumeFilename.toLowerCase().endsWith(".doc")) {
                    contentType = "application/msword";
                } else if (resumeFilename.toLowerCase().endsWith(".docx")) {
                    contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                }
            }
            
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resumeFilename + "\"")
                .body(resumeContent);
                
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to download resume: " + e.getMessage()
            ));
        }
    }
    
    @PostMapping("/{candidateId}/summary")
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
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Resume summary updated successfully",
                "summary", newSummary.trim()
            ));
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to update resume summary: " + e.getMessage()
            ));
        }
    }
    
    @GetMapping("/{candidateId}/history")
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
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to get resume history: " + e.getMessage()
            ));
        }
    }
    
    @PostMapping("/bulk-process")
    public ResponseEntity<?> bulkProcessResumes(@RequestHeader("X-User-Id") String userId,
                                               @RequestHeader("X-User-Role") String userRole) {
        try {
            if (!"ADMIN".equals(userRole) && !"SUPER_ADMIN".equals(userRole)) {
                return ResponseEntity.status(403).body(Map.of("error", "Only admins can trigger bulk processing"));
            }
            
            BulkResumeProcessingService.BulkProcessingResult result = 
                bulkProcessingService.processCandidatesWithoutSummaries(userId, userRole);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to start bulk processing: " + e.getMessage()
            ));
        }
    }
    
    @GetMapping("/processing-stats")
    public ResponseEntity<?> getProcessingStats(@RequestHeader("X-User-Id") String userId,
                                               @RequestHeader("X-User-Role") String userRole) {
        try {
            if (!"ADMIN".equals(userRole) && !"SUPER_ADMIN".equals(userRole) && !"RECRUITER".equals(userRole)) {
                return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
            }
            
            BulkResumeProcessingService.ResumeProcessingStats stats = 
                bulkProcessingService.getProcessingStats(userId, userRole);
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to get processing stats: " + e.getMessage()
            ));
        }
    }
    
    @GetMapping("/analytics")
    public ResponseEntity<?> getResumeAnalytics(@RequestHeader("X-User-Id") String userId,
                                               @RequestHeader("X-User-Role") String userRole) {
        try {
            if (!"ADMIN".equals(userRole) && !"SUPER_ADMIN".equals(userRole) && !"RECRUITER".equals(userRole)) {
                return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
            }
            
            ResumeAnalyticsService.ResumeAnalytics analytics = 
                analyticsService.getResumeAnalytics(userId, userRole);
            
            return ResponseEntity.ok(analytics);
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to get resume analytics: " + e.getMessage()
            ));
        }
    }
    
    @GetMapping("/analytics/trends")
    public ResponseEntity<?> getUploadTrends(@RequestParam(defaultValue = "30") int days,
                                            @RequestHeader("X-User-Id") String userId,
                                            @RequestHeader("X-User-Role") String userRole) {
        try {
            if (!"ADMIN".equals(userRole) && !"SUPER_ADMIN".equals(userRole) && !"RECRUITER".equals(userRole)) {
                return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
            }
            
            ResumeAnalyticsService.UploadTrends trends = 
                analyticsService.getUploadTrends(userId, userRole, days);
            
            return ResponseEntity.ok(trends);
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to get upload trends: " + e.getMessage()
            ));
        }
    }
}