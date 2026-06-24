package com.benchreadiness.interview.controller;

import com.benchreadiness.interview.dto.ClientBriefDto;
import com.benchreadiness.interview.dto.AutoFillPreview;
import com.benchreadiness.interview.dto.CandidateMatchingResult;
import com.benchreadiness.interview.dto.CandidateReviewSummary;
import com.benchreadiness.interview.dto.CompleteInterviewRequest;
import com.benchreadiness.interview.dto.CreateInterviewRequest;
import com.benchreadiness.interview.dto.ProctoringEventsRequest;
import com.benchreadiness.interview.dto.RecordAnswerRequest;
import com.benchreadiness.interview.dto.RecordQuestionRequest;
import com.benchreadiness.interview.entity.Interview;
import com.benchreadiness.interview.entity.InterviewQuestion;
import com.benchreadiness.interview.exception.VideoProctoringNotRequiredException;
import com.benchreadiness.interview.security.StaffSecurityRoles;
import com.benchreadiness.interview.service.ClientBriefPdfGenerationService;
import com.benchreadiness.interview.service.ClientBriefService;
import com.benchreadiness.interview.service.BranchInterviewValidator;
import com.benchreadiness.interview.service.CandidateMatchingService;
import com.benchreadiness.interview.service.CandidateReviewService;
import com.benchreadiness.interview.service.InterviewService;
import com.benchreadiness.interview.service.EnhancedInterviewService;
import com.benchreadiness.interview.service.InterviewQuestionService;
import com.benchreadiness.interview.service.PdfGenerationService;
import com.benchreadiness.interview.service.ProctoringService;
import com.benchreadiness.interview.service.RecordingService;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/interviews")
public class InterviewController {

    @Value("${app.recording.upload-dir:${java.io.tmpdir}/br-recordings}")
    private String recordingUploadDir;

    private final InterviewService interviewService;
    private final EnhancedInterviewService enhancedInterviewService;
    private final CandidateMatchingService candidateMatchingService;
    private final CandidateReviewService candidateReviewService;
    private final PdfGenerationService pdfGenerationService;
    private final InterviewQuestionService interviewQuestionService;
    private final RecordingService recordingService;
    private final ProctoringService proctoringService;
    private final BranchInterviewValidator branchInterviewValidator;
    private final ClientBriefService clientBriefService;
    private final ClientBriefPdfGenerationService clientBriefPdfGenerationService;

    public InterviewController(InterviewService interviewService,
                              EnhancedInterviewService enhancedInterviewService,
                              CandidateMatchingService candidateMatchingService,
                              CandidateReviewService candidateReviewService,
                              PdfGenerationService pdfGenerationService,
                              InterviewQuestionService interviewQuestionService,
                              RecordingService recordingService,
                              ProctoringService proctoringService,
                              BranchInterviewValidator branchInterviewValidator,
                              ClientBriefService clientBriefService,
                              ClientBriefPdfGenerationService clientBriefPdfGenerationService) {
        this.interviewService = interviewService;
        this.enhancedInterviewService = enhancedInterviewService;
        this.candidateMatchingService = candidateMatchingService;
        this.candidateReviewService = candidateReviewService;
        this.pdfGenerationService = pdfGenerationService;
        this.interviewQuestionService = interviewQuestionService;
        this.recordingService = recordingService;
        this.proctoringService = proctoringService;
        this.branchInterviewValidator = branchInterviewValidator;
        this.clientBriefService = clientBriefService;
        this.clientBriefPdfGenerationService = clientBriefPdfGenerationService;
    }

    @GetMapping("/auto-fill/preview")
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ + "')")
    public ResponseEntity<?> previewAutoFill(@RequestParam(required = false) String candidateId,
                                            @RequestParam(required = false) String clientId,
                                            @RequestHeader("X-User-Role") String userRole) {
        try {
            AutoFillPreview preview = enhancedInterviewService.previewAutoFill(candidateId, clientId, userRole);
            return ResponseEntity.ok(preview);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ + "')")
    public ResponseEntity<?> create(@Valid @RequestBody CreateInterviewRequest req,
                                     @RequestHeader("X-User-Id") String userId,
                                     @RequestHeader("X-User-Role") String userRole) {
        try {
            String branch = branchInterviewValidator.validateAndResolveBranch(req, userId, userRole);
            UUID clientUuid = branchInterviewValidator.parseClientId(req.getClientId());
            Interview interview;
            if (req.getCandidateId() != null && !req.getCandidateId().isBlank()) {
                interview = enhancedInterviewService.createInterviewWithAutoFill(
                        req, userId, userRole, branch, clientUuid);
            } else {
                interview = interviewService.createInterview(req, userId, branch, clientUuid);
            }
            return ResponseEntity.ok(Map.of("id", interview.getId(), "status", interview.getStatus()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.ADMIN + "')")
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
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ_AND_CANDIDATE + "')")
    public ResponseEntity<?> getById(@PathVariable String id,
                                     @RequestHeader(value = "X-User-Id", required = false) String userId,
                                     @RequestHeader(value = "X-User-Role", required = false) String userRole,
                                     @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
        if (isStaffReadRole(userRole)) {
            return interviewService.findByIdForRole(id, userId, userRole)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }
        return interviewService.findByIdForCandidate(id, userEmail)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private static boolean isStaffReadRole(String userRole) {
        if (userRole == null) {
            return false;
        }
        return switch (userRole) {
            case "ADMIN", "TESTING_ADMIN", "SUPER_ADMIN", "RECRUITER", "TESTING_RECRUITER" -> true;
            default -> false;
        };
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ + "')")
    public ResponseEntity<List<Interview>> getAll(@RequestHeader("X-User-Id") String userId,
                                                   @RequestHeader("X-User-Role") String userRole) {
        return ResponseEntity.ok(interviewService.findAllForRole(userId, userRole));
    }

    @GetMapping("/today")
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ + "')")
    public ResponseEntity<?> getToday(@RequestHeader("X-User-Id") String userId,
                                      @RequestHeader("X-User-Role") String userRole) {
        return ResponseEntity.ok(interviewService.getTodaysSummaries(userId, userRole));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ + "')")
    public ResponseEntity<List<com.benchreadiness.interview.dto.InterviewSummaryDto>> getSummary(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        return ResponseEntity.ok(interviewService.getSummaries(userId, userRole));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<List<Interview>> getMine(@RequestHeader("X-User-Email") String email) {
        return ResponseEntity.ok(interviewService.findByEmail(email));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ_AND_CANDIDATE + "')")
    public ResponseEntity<?> startLive(@PathVariable String id) {
        try {
            Interview updated = interviewService.startLiveInterview(id);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/assessment-status")
    @PatchMapping("/{id}/assessment-status")
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ + "')")
    public ResponseEntity<?> updateAssessmentStatus(@PathVariable String id,
                                                    @RequestBody Map<String, Object> body) {
        try {
            String status = body.get("status") != null ? String.valueOf(body.get("status")) : null;
            String error = body.get("error") != null ? String.valueOf(body.get("error")) : null;
            String resultJson = body.get("resultJson") != null ? String.valueOf(body.get("resultJson")) : null;
            String runId = body.get("runId") != null ? String.valueOf(body.get("runId")) : null;
            Interview updated = interviewService.updateAssessmentStatus(id, status, error, resultJson, runId);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ_AND_CANDIDATE + "')")
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
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ + "')")
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
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ_AND_CANDIDATE + "')")
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
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ_AND_CANDIDATE + "')")
    public ResponseEntity<?> getJd(@PathVariable String jdId) {
        return interviewService.findJdById(jdId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/plans/{planId}")
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ_AND_CANDIDATE + "')")
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
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ + "')")
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
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ + "')")
    public ResponseEntity<?> refreshCandidateClientMatches(@PathVariable String candidateId) {
        try {
            CandidateMatchingResult result = candidateMatchingService.getCandidateClientMatches(candidateId, true);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/candidates/{candidateId}/review-summary/download")
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ + "')")
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

    @GetMapping("/{id}/client-brief/view")
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ + "')")
    public ResponseEntity<Void> viewClientBriefPage(@PathVariable String id,
                                                    @RequestHeader("X-User-Id") String userId,
                                                    @RequestHeader("X-User-Role") String userRole) {
        if (interviewService.findByIdForRole(id, userId, userRole).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.status(302)
            .header("Location", "/client-brief.html?interviewId=" + id)
            .build();
    }

    @GetMapping("/{id}/client-brief")
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ + "')")
    public ResponseEntity<?> getClientBrief(@PathVariable String id,
                                            @RequestHeader("X-User-Id") String userId,
                                            @RequestHeader("X-User-Role") String userRole) {
        try {
            return interviewService.findByIdForRole(id, userId, userRole)
                .map(interview -> ResponseEntity.ok(clientBriefService.getClientBrief(id, userId)))
                .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/client-brief/generate")
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ + "')")
    public ResponseEntity<?> generateClientBrief(@PathVariable String id,
                                               @RequestHeader("X-User-Id") String userId,
                                               @RequestHeader("X-User-Role") String userRole) {
        try {
            return interviewService.findByIdForRole(id, userId, userRole)
                .map(interview -> ResponseEntity.ok(clientBriefService.generateClientBrief(id, userId)))
                .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (feign.RetryableException e) {
            return ResponseEntity.status(504).body(Map.of(
                "error", "Client brief generation timed out. The AI may still be processing — try again in a minute."));
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(InterviewController.class)
                .error("Client brief generation failed for {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to generate client brief"));
        }
    }

    @PutMapping("/{id}/client-brief")
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ + "')")
    public ResponseEntity<?> saveClientBrief(@PathVariable String id,
                                             @RequestBody ClientBriefDto brief,
                                             @RequestHeader("X-User-Id") String userId,
                                             @RequestHeader("X-User-Role") String userRole,
                                             @RequestHeader(value = "X-User-Name", required = false) String userName) {
        try {
            return interviewService.findByIdForRole(id, userId, userRole)
                .map(interview -> ResponseEntity.ok(
                    clientBriefService.saveClientBrief(id, brief, userId,
                        userName != null && !userName.isBlank() ? userName : "Staff")))
                .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/client-brief/download")
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ + "')")
    public ResponseEntity<byte[]> downloadClientBrief(@PathVariable String id,
                                                      @RequestHeader("X-User-Id") String userId,
                                                      @RequestHeader("X-User-Role") String userRole) {
        try {
            if (interviewService.findByIdForRole(id, userId, userRole).isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            var pdfContext = clientBriefService.getPdfContext(id, userId);
            byte[] pdfBytes = clientBriefPdfGenerationService.generateClientBriefPdf(pdfContext);
            String candidateName = pdfContext.context().candidateName().replaceAll("\\s+", "_");
            String filename = candidateName + "_Client_Evaluation_Brief.pdf";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
            return ResponseEntity.ok().headers(headers).body(pdfBytes);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .header("X-Error-Message", e.getMessage())
                .build();
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .header("X-Error-Message", e.getMessage())
                .build();
        }
    }

    /** Persist bot question for a slot (called after each next-question). */
    @PostMapping("/{id}/questions")
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ_AND_CANDIDATE + "')")
    public ResponseEntity<?> recordQuestion(@PathVariable String id,
                                            @RequestBody RecordQuestionRequest req) {
        try {
            InterviewQuestion saved = interviewQuestionService.recordQuestion(id, req);
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "id", saved.getId(),
                "slot", saved.getSlotNumber()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Persist candidate answer for a slot. */
    @PostMapping("/{id}/answers")
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ_AND_CANDIDATE + "')")
    public ResponseEntity<?> recordAnswer(@PathVariable String id,
                                          @RequestBody RecordAnswerRequest req) {
        try {
            InterviewQuestion saved = interviewQuestionService.recordAnswer(id, req);
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "id", saved.getId(),
                "slot", saved.getSlotNumber()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/questions")
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ + "')")
    public ResponseEntity<?> listQuestions(@PathVariable String id) {
        try {
            return ResponseEntity.ok(interviewQuestionService.listByInterview(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Append a session recording chunk (upload during interview). */
    @PostMapping(value = "/{id}/recording/chunk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ_AND_CANDIDATE + "')")
    public ResponseEntity<?> uploadRecordingChunk(@PathVariable String id,
                                                  @RequestParam("chunk") MultipartFile chunk,
                                                  @RequestParam(value = "chunkIndex", defaultValue = "0") int chunkIndex,
                                                  @RequestParam(value = "isFinal", defaultValue = "false") boolean isFinal) {
        try {
            return ResponseEntity.ok(recordingService.appendChunk(id, chunk, chunkIndex, isFinal));
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(InterviewController.class)
                .error("Recording chunk upload failed for interview {} chunk {}: {}", id, chunkIndex, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Upload session recording (single file — legacy / final upload). */
    @PostMapping(value = "/{id}/recording", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ_AND_CANDIDATE + "')")
    public ResponseEntity<?> uploadRecording(@PathVariable String id,
                                             @RequestParam("recording") MultipartFile file) {
        try {
            return ResponseEntity.ok(recordingService.uploadFull(id, file));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Download / stream session recording (admin/recruiter only). */
    @GetMapping("/{id}/recording")
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ + "')")
    public ResponseEntity<Resource> downloadRecording(@PathVariable String id) {
        try {
            Interview interview = interviewService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Interview not found: " + id));

            if (interview.getRecordingPath() == null) {
                return ResponseEntity.notFound().build();
            }

            File file = new File(interview.getRecordingPath());
            // Defense-in-depth: ensure the resolved path stays within the upload directory
            try {
                Path canonical = file.toPath().toRealPath();
                Path allowedDir = Paths.get(recordingUploadDir).toAbsolutePath().normalize();
                if (!canonical.startsWith(allowedDir)) {
                    return ResponseEntity.status(403).build();
                }
            } catch (IOException e) {
                return ResponseEntity.notFound().build();
            }
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

    @PostMapping("/{id}/proctoring/events")
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ_AND_CANDIDATE + "')")
    public ResponseEntity<?> appendProctoringEvents(@PathVariable String id,
                                                    @RequestBody ProctoringEventsRequest req) {
        try {
            return ResponseEntity.ok(proctoringService.appendEvents(id, req));
        } catch (VideoProctoringNotRequiredException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping(value = "/{id}/proctoring/snapshot", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ_AND_CANDIDATE + "')")
    public ResponseEntity<?> uploadProctoringSnapshot(@PathVariable String id,
                                                      @RequestParam("snapshot") MultipartFile snapshot,
                                                      @RequestParam(value = "eventType", required = false) String eventType) {
        try {
            return ResponseEntity.ok(proctoringService.saveSnapshot(id, snapshot, eventType));
        } catch (VideoProctoringNotRequiredException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/proctoring/timeline")
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ + "')")
    public ResponseEntity<?> getProctoringTimeline(@PathVariable String id) {
        try {
            return ResponseEntity.ok(proctoringService.getTimeline(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/proctoring/snapshots/{fileName}")
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ + "')")
    public ResponseEntity<Resource> downloadProctoringSnapshot(@PathVariable String id,
                                                             @PathVariable String fileName) {
        try {
            Path file = proctoringService.resolveSnapshot(id, fileName);
            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
