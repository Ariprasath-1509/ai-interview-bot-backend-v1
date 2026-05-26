package com.benchreadiness.interview.controller;

import com.benchreadiness.interview.dto.AutoFillPreview;
import com.benchreadiness.interview.dto.CandidateMatchingResult;
import com.benchreadiness.interview.dto.CandidateReviewSummary;
import com.benchreadiness.interview.dto.CompleteInterviewRequest;
import com.benchreadiness.interview.dto.CreateInterviewRequest;
import com.benchreadiness.interview.entity.Interview;
import com.benchreadiness.interview.service.CandidateMatchingService;
import com.benchreadiness.interview.service.CandidateReviewService;
import com.benchreadiness.interview.service.InterviewService;
import com.benchreadiness.interview.service.EnhancedInterviewService;
import com.benchreadiness.interview.service.PdfGenerationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/interviews")
public class InterviewController {

    private final InterviewService interviewService;
    private final EnhancedInterviewService enhancedInterviewService;
    private final CandidateMatchingService candidateMatchingService;
    private final CandidateReviewService candidateReviewService;
    private final PdfGenerationService pdfGenerationService;

    @Value("${app.recording.upload-dir:${java.io.tmpdir}/br-recordings}")
    private String uploadDir;

    public InterviewController(InterviewService interviewService,
                              EnhancedInterviewService enhancedInterviewService,
                              CandidateMatchingService candidateMatchingService,
                              CandidateReviewService candidateReviewService,
                              PdfGenerationService pdfGenerationService) {
        this.interviewService = interviewService;
        this.enhancedInterviewService = enhancedInterviewService;
        this.candidateMatchingService = candidateMatchingService;
        this.candidateReviewService = candidateReviewService;
        this.pdfGenerationService = pdfGenerationService;
    }

    @GetMapping("/auto-fill/preview")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<?> previewAutoFill(@RequestParam(required = false) String candidateId,
                                            @RequestParam(required = false) String clientId) {
        try {
            AutoFillPreview preview = enhancedInterviewService.previewAutoFill(candidateId, clientId);
            return ResponseEntity.ok(preview);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<?> create(@Valid @RequestBody CreateInterviewRequest req,
                                     @RequestHeader("X-User-Id") String userId) {
        try {
            Interview interview;
            if (req.getCandidateId() != null) {
                interview = enhancedInterviewService.createInterviewWithAutoFill(req, userId);
            } else {
                interview = interviewService.createInterview(req, userId);
            }
            return ResponseEntity.ok(Map.of("id", interview.getId(), "status", interview.getStatus()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<?> delete(@PathVariable String id) {
        try {
            boolean deleted = interviewService.deleteInterview(id);
            if (deleted) {
                return ResponseEntity.ok(Map.of("message", "Interview deleted successfully"));
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable String id) {
        return interviewService.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<Interview>> getAll() {
        return ResponseEntity.ok(interviewService.findAll());
    }

    @GetMapping("/today")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<?> getToday() {
        return ResponseEntity.ok(interviewService.getTodaysSummaries());
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<List<com.benchreadiness.interview.dto.InterviewSummaryDto>> getSummary() {
        return ResponseEntity.ok(interviewService.getSummaries());
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<List<Interview>> getMine(@RequestHeader("X-User-Email") String email) {
        return ResponseEntity.ok(interviewService.findByEmail(email));
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<?> complete(@PathVariable String id,
                                       @RequestBody CompleteInterviewRequest req) {
        try {
            Interview updated = interviewService.completeInterview(id, req);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id,
                                     @RequestBody Map<String, String> updates) {
        try {
            Interview updated = interviewService.updateInterview(id, updates);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/abandon")
    public ResponseEntity<?> abandon(@PathVariable String id,
                                      @RequestBody com.benchreadiness.interview.dto.AbandonInterviewRequest req) {
        try {
            Interview updated = interviewService.abandonInterview(id, req);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/jd/{jdId}")
    public ResponseEntity<?> getJd(@PathVariable String jdId) {
        return interviewService.findJdById(jdId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/plans/{planId}")
    public ResponseEntity<?> getPlan(@PathVariable String planId) {
        return interviewService.findPlanById(planId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/recalculate-system-interview-counts")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> recalculateSystemInterviewCounts() {
        try {
            Map<String, Object> result = interviewService.recalculateAllSystemInterviewCounts();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/candidates/{candidateId}/client-matches")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<?> getCandidateClientMatches(@PathVariable String candidateId,
                                                       @RequestParam(defaultValue = "false") boolean forceRefresh) {
        try {
            CandidateMatchingResult result = candidateMatchingService.getCandidateClientMatches(candidateId, forceRefresh);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/candidates/{candidateId}/refresh-client-matches")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<?> refreshCandidateClientMatches(@PathVariable String candidateId) {
        try {
            CandidateMatchingResult result = candidateMatchingService.getCandidateClientMatches(candidateId, true);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/candidates/{candidateId}/review-summary/download")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<byte[]> downloadCandidateReviewSummary(@PathVariable String candidateId,
                                                                 @RequestHeader("X-User-Id") String userId) {
        try {
            CandidateReviewSummary summary = candidateReviewService.getCandidateReviewSummary(candidateId, userId);
            
            // Check if candidate has any interviews
            if (summary.getInterviews() == null || summary.getInterviews().isEmpty()) {
                return ResponseEntity.badRequest()
                        .header("X-Error-Message", "No completed interviews found for this candidate")
                        .build();
            }
            
            byte[] pdfBytes = pdfGenerationService.generateCandidateReviewPdf(summary);
            
            String filename = summary.getCandidateInfo().getName().replaceAll("\\s+", "_") + "_Review_Summary.pdf";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfBytes);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .header("X-Error-Message", e.getMessage())
                    .build();
        }
    }

    /** Upload session recording (candidate calls this after interview). */
    @PostMapping(value = "/{id}/recording", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadRecording(@PathVariable String id,
                                             @RequestParam("recording") MultipartFile file) {
        try {
            Interview interview = interviewService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Interview not found: " + id));

            Path dir = Paths.get(uploadDir);
            Files.createDirectories(dir);

            String ext = ".webm";
            String orig = file.getOriginalFilename();
            if (orig != null && orig.contains(".")) ext = orig.substring(orig.lastIndexOf('.'));

            Path dest = dir.resolve("rec_" + id + "_" + UUID.randomUUID() + ext);
            file.transferTo(dest.toFile());

            interview.setRecordingPath(dest.toAbsolutePath().toString());
            interviewService.saveInterview(interview);

            return ResponseEntity.ok(Map.of("ok", true, "path", dest.getFileName().toString()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Download / stream session recording (admin/recruiter only). */
    @GetMapping("/{id}/recording")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<Resource> downloadRecording(@PathVariable String id) {
        try {
            Interview interview = interviewService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Interview not found: " + id));

            if (interview.getRecordingPath() == null) {
                return ResponseEntity.notFound().build();
            }

            File file = new File(interview.getRecordingPath());
            if (!file.exists()) return ResponseEntity.notFound().build();

            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("audio/webm"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"recording_" + id + ".webm\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
