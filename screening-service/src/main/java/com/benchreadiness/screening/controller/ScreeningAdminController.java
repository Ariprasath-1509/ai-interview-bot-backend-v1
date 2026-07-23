package com.benchreadiness.screening.controller;

import com.benchreadiness.screening.dto.CreateBatchFromDocumentRequest;
import com.benchreadiness.screening.dto.CreateBatchRequest;
import com.benchreadiness.screening.dto.Round2FeedbackRequest;
import com.benchreadiness.screening.dto.Round3FeedbackRequest;
import com.benchreadiness.screening.dto.UpdateDeadlineRequest;
import com.benchreadiness.screening.entity.ScreeningAnswer;
import com.benchreadiness.screening.entity.ScreeningBatch;
import com.benchreadiness.screening.entity.ScreeningCandidate;
import com.benchreadiness.screening.mail.ScreeningMailService;
import com.benchreadiness.screening.repository.ScreeningBatchRepository;
import com.benchreadiness.screening.service.BatchService;
import com.benchreadiness.screening.service.DocumentTextExtractionService;
import com.benchreadiness.screening.service.PipelineService;
import com.benchreadiness.screening.service.QuestionGenerationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/screening/admin")
public class ScreeningAdminController {

    private static final Logger log = LoggerFactory.getLogger(ScreeningAdminController.class);
    // hasAnyRole(String...) needs each role as a separate quoted arg — the ', ' here is load-bearing,
    // not just a plain comma, otherwise Spring Security treats this as one literal (unmatchable) role name.
    private static final String STAFF_ROLES = "RECRUITER', 'TESTING_RECRUITER', 'ADMIN', 'TESTING_ADMIN', 'SUPER_ADMIN";
    private static final String MANAGER_ROLES = "ADMIN', 'TESTING_ADMIN', 'SUPER_ADMIN";

    private final BatchService batchService;
    private final PipelineService pipelineService;
    private final ScreeningBatchRepository batchRepository;
    private final ScreeningMailService mailService;
    private final DocumentTextExtractionService documentTextExtractionService;

    public ScreeningAdminController(BatchService batchService, PipelineService pipelineService,
                                    ScreeningBatchRepository batchRepository, ScreeningMailService mailService,
                                    DocumentTextExtractionService documentTextExtractionService) {
        this.batchService = batchService;
        this.pipelineService = pipelineService;
        this.batchRepository = batchRepository;
        this.mailService = mailService;
        this.documentTextExtractionService = documentTextExtractionService;
    }

    @GetMapping("/languages")
    @PreAuthorize("hasAnyRole('" + STAFF_ROLES + "')")
    public ResponseEntity<?> languages() {
        return ResponseEntity.ok(Map.of("languages", QuestionGenerationService.supportedLanguages()));
    }

    @PostMapping("/batches")
    @PreAuthorize("hasAnyRole('" + STAFF_ROLES + "')")
    public ResponseEntity<?> createBatch(@Valid @RequestBody CreateBatchRequest req,
                                         @RequestHeader("X-User-Id") String userId,
                                         @RequestHeader(value = "X-User-Email", required = false) String userEmail,
                                         @RequestHeader(value = "X-User-Name", required = false) String userName) {
        try {
            ScreeningBatch batch = batchService.createBatch(req, userId,
                    userEmail != null ? userEmail : "", userName);
            return ResponseEntity.ok(Map.of("ok", true, "batchId", batch.getId()));
        } catch (Exception e) {
            log.error("Failed to create screening batch", e);
            return ResponseEntity.status(500).body(Map.of("ok", false, "error", "Failed to create batch: " + e.getMessage()));
        }
    }

    /** Extracts plain text from an uploaded .docx (JD or question paper) for the recruiter to review before generating questions. */
    @PostMapping(value = "/extract-document-text", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('" + STAFF_ROLES + "')")
    public ResponseEntity<?> extractDocumentText(@RequestParam("file") MultipartFile file) {
        try {
            String text = documentTextExtractionService.extractText(file);
            return ResponseEntity.ok(Map.of("ok", true, "text", text));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to extract text from uploaded document", e);
            return ResponseEntity.status(500).body(Map.of("ok", false, "error", "Failed to read this document"));
        }
    }

    @PostMapping("/batches/from-document")
    @PreAuthorize("hasAnyRole('" + STAFF_ROLES + "')")
    public ResponseEntity<?> createBatchFromDocument(@Valid @RequestBody CreateBatchFromDocumentRequest req,
                                                     @RequestHeader("X-User-Id") String userId,
                                                     @RequestHeader(value = "X-User-Email", required = false) String userEmail,
                                                     @RequestHeader(value = "X-User-Name", required = false) String userName) {
        try {
            ScreeningBatch batch = batchService.createBatchFromDocument(req, userId,
                    userEmail != null ? userEmail : "", userName);
            return ResponseEntity.ok(Map.of("ok", true, "batchId", batch.getId()));
        } catch (Exception e) {
            log.error("Failed to create screening batch from document", e);
            return ResponseEntity.status(500).body(Map.of("ok", false, "error", "Failed to create batch: " + e.getMessage()));
        }
    }

    @PatchMapping("/batches/{batchId}/deadline")
    @PreAuthorize("hasAnyRole('" + STAFF_ROLES + "')")
    public ResponseEntity<?> updateDeadline(@PathVariable String batchId, @Valid @RequestBody UpdateDeadlineRequest req) {
        return handle(() -> batchSummary(batchService.updateDeadline(batchId, req.getDeadline())));
    }

    @DeleteMapping("/batches/{batchId}")
    @PreAuthorize("hasAnyRole('" + STAFF_ROLES + "')")
    public ResponseEntity<?> deleteBatch(@PathVariable String batchId) {
        return handle(() -> {
            batchService.deleteBatch(batchId);
            return Map.of("ok", true);
        });
    }

    @PostMapping("/batches/{batchId}/candidates")
    @PreAuthorize("hasAnyRole('" + STAFF_ROLES + "')")
    public ResponseEntity<?> addCandidate(@PathVariable String batchId, @Valid @RequestBody CreateBatchRequest.CandidateEntry req) {
        return handle(() -> candidateSummary(batchService.addCandidate(batchId, req)));
    }

    /** For a candidate who missed Round 1 (e.g. showed up late) — skips straight to the Round 2 queue. */
    @PostMapping("/candidates/direct-to-round2")
    @PreAuthorize("hasAnyRole('" + STAFF_ROLES + "')")
    public ResponseEntity<?> addDirectToRound2(@Valid @RequestBody CreateBatchRequest.CandidateEntry req,
                                               @RequestHeader("X-User-Id") String userId,
                                               @RequestHeader(value = "X-User-Email", required = false) String userEmail,
                                               @RequestHeader(value = "X-User-Name", required = false) String userName) {
        return handle(() -> candidateSummary(
                batchService.addDirectToRound2(req, userId, userEmail != null ? userEmail : "", userName)));
    }

    @DeleteMapping("/candidates/{candidateId}")
    @PreAuthorize("hasAnyRole('" + STAFF_ROLES + "')")
    public ResponseEntity<?> removeCandidate(@PathVariable String candidateId) {
        return handle(() -> {
            batchService.removeCandidate(candidateId);
            return Map.of("ok", true);
        });
    }

    @GetMapping("/batches")
    @PreAuthorize("hasAnyRole('" + STAFF_ROLES + "')")
    public ResponseEntity<?> listBatches() {
        List<Map<String, Object>> batches = batchRepository.findAll().stream()
                .filter(b -> !BatchService.DIRECT_ENTRY_LANGUAGE.equals(b.getLanguage()))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::batchSummary)
                .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("batches", batches));
    }

    @GetMapping("/batches/{batchId}/candidates")
    @PreAuthorize("hasAnyRole('" + STAFF_ROLES + "')")
    public ResponseEntity<?> batchCandidates(@PathVariable String batchId) {
        List<Map<String, Object>> candidates = pipelineService.round1Results(batchId).stream()
                .map(this::candidateSummary).collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("candidates", candidates));
    }

    /**
     * Final consolidated view of a batch after Round 3: every candidate's full record across all 3 rounds —
     * Round 1 answers with per-question scores/AI feedback, Round 2 interviewer feedback, Round 3 managerial
     * rubric + concluding comments — plus totals and outcome counts.
     */
    @GetMapping("/batches/{batchId}/summary")
    @PreAuthorize("hasAnyRole('" + STAFF_ROLES + "')")
    public ResponseEntity<?> batchFinalSummary(@PathVariable String batchId) {
        List<ScreeningCandidate> candidates = pipelineService.round1Results(batchId);

        List<Map<String, Object>> rows = candidates.stream().map(c -> {
            Map<String, Object> m = candidateSummary(c);
            m.put("finalStatus", finalStatus(c));
            m.put("totalMarks", totalMarks(c));
            m.put("round1Answers", pipelineService.candidateAnswers(c.getId()).stream()
                    .map(this::answerDetail).collect(Collectors.toList()));
            return m;
        }).collect(Collectors.toList());

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("total", (long) candidates.size());
        counts.put("selected", candidates.stream().filter(c -> "Selected".equals(finalStatus(c))).count());
        counts.put("rejected", candidates.stream().filter(c -> "Rejected".equals(finalStatus(c))).count());
        counts.put("hold", candidates.stream().filter(c -> "Hold".equals(finalStatus(c))).count());
        counts.put("inProgress", candidates.stream().filter(c -> "In Progress".equals(finalStatus(c))).count());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("counts", counts);
        response.put("candidates", rows);
        return ResponseEntity.ok(response);
    }

    private String finalStatus(ScreeningCandidate c) {
        return switch (c.getStage()) {
            case CONVERTED -> "Selected";
            case ROUND1_FAILED, ROUND2_REJECTED, ROUND3_REJECTED -> "Rejected";
            case ROUND2_HOLD, ROUND3_HOLD -> "Hold";
            default -> "In Progress";
        };
    }

    private Double totalMarks(ScreeningCandidate c) {
        if (c.getRound1Score() == null) return null;
        double total = c.getRound1Score();
        if (c.getRound2Marks() != null) total += c.getRound2Marks();
        if (c.getRound3Total() != null) total += c.getRound3Total();
        return total;
    }

    @GetMapping("/candidates/{candidateId}/answers")
    @PreAuthorize("hasAnyRole('" + STAFF_ROLES + "')")
    public ResponseEntity<?> candidateAnswers(@PathVariable String candidateId) {
        List<Map<String, Object>> answers = pipelineService.candidateAnswers(candidateId).stream()
                .map(this::answerDetail).collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("answers", answers));
    }

    /** Lets a recruiter correct a score when the AI grading was wrong. */
    @PatchMapping("/answers/{answerId}")
    @PreAuthorize("hasAnyRole('" + STAFF_ROLES + "')")
    public ResponseEntity<?> correctAnswerScore(@PathVariable String answerId, @RequestBody Map<String, Object> body) {
        return handle(() -> {
            if (!(body.get("score") instanceof Number scoreNum)) {
                throw new IllegalArgumentException("A numeric score is required");
            }
            return answerDetail(pipelineService.correctAnswerScore(answerId, scoreNum.doubleValue()));
        });
    }

    @PostMapping("/candidates/{candidateId}/round1-decision")
    @PreAuthorize("hasAnyRole('" + STAFF_ROLES + "')")
    public ResponseEntity<?> round1Decision(@PathVariable String candidateId, @RequestBody Map<String, Boolean> body) {
        return handle(() -> candidateSummary(pipelineService.markRound1(candidateId, Boolean.TRUE.equals(body.get("passed")))));
    }

    /** Recruiter rates the candidate P0–P3 after reviewing Round 1; carried through to Round 2/3 queues. body.priority may be null to clear it. */
    @PostMapping("/candidates/{candidateId}/round1-priority")
    @PreAuthorize("hasAnyRole('" + STAFF_ROLES + "')")
    public ResponseEntity<?> round1Priority(@PathVariable String candidateId, @RequestBody Map<String, String> body) {
        return handle(() -> {
            String raw = body.get("priority");
            com.benchreadiness.screening.entity.enums.CandidatePriority priority = null;
            if (raw != null && !raw.isBlank()) {
                try {
                    priority = com.benchreadiness.screening.entity.enums.CandidatePriority.valueOf(raw);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Invalid priority: " + raw);
                }
            }
            return candidateSummary(pipelineService.setRound1Priority(candidateId, priority));
        });
    }

    /** Lets staff accept a submission (or reopen the test) even after the batch deadline has passed. */
    @PostMapping("/candidates/{candidateId}/allow-late-submission")
    @PreAuthorize("hasAnyRole('" + STAFF_ROLES + "')")
    public ResponseEntity<?> allowLateSubmission(@PathVariable String candidateId, @RequestBody Map<String, Boolean> body) {
        return handle(() -> candidateSummary(pipelineService.setAllowLateSubmission(candidateId, !Boolean.FALSE.equals(body.get("allow")))));
    }

    /** Lets staff permit a candidate whose test paused on a proctoring violation to resume on the same link.
     *  body.allow=true (default) grants permission (unlocks); allow=false re-locks it. */
    @PostMapping("/candidates/{candidateId}/allow-continue-after-violation")
    @PreAuthorize("hasAnyRole('" + STAFF_ROLES + "')")
    public ResponseEntity<?> allowContinueAfterViolation(@PathVariable String candidateId, @RequestBody Map<String, Boolean> body) {
        boolean allow = !Boolean.FALSE.equals(body.get("allow"));
        return handle(() -> candidateSummary(pipelineService.setViolationLocked(candidateId, !allow)));
    }

    @GetMapping("/round2/queue")
    @PreAuthorize("hasAnyRole('" + STAFF_ROLES + "')")
    public ResponseEntity<?> round2Queue() {
        return ResponseEntity.ok(Map.of("candidates",
                pipelineService.round2Queue().stream().map(this::candidateSummary).collect(Collectors.toList())));
    }

    @PostMapping("/candidates/{candidateId}/round2/start")
    @PreAuthorize("hasAnyRole('" + STAFF_ROLES + "')")
    public ResponseEntity<?> startRound2(@PathVariable String candidateId) {
        return handle(() -> candidateSummary(pipelineService.startRound2(candidateId)));
    }

    @PostMapping("/candidates/{candidateId}/round2/feedback")
    @PreAuthorize("hasAnyRole('" + STAFF_ROLES + "')")
    public ResponseEntity<?> round2Feedback(@PathVariable String candidateId, @Valid @RequestBody Round2FeedbackRequest req,
                                            @RequestHeader("X-User-Id") String userId) {
        return handle(() -> candidateSummary(pipelineService.submitRound2Feedback(candidateId, req, userId)));
    }

    @GetMapping("/round3/queue")
    @PreAuthorize("hasAnyRole('" + MANAGER_ROLES + "')")
    public ResponseEntity<?> round3Queue() {
        return ResponseEntity.ok(Map.of("candidates",
                pipelineService.round3Queue().stream().map(this::candidateSummary).collect(Collectors.toList())));
    }

    @PostMapping("/candidates/{candidateId}/round3/start")
    @PreAuthorize("hasAnyRole('" + MANAGER_ROLES + "')")
    public ResponseEntity<?> startRound3(@PathVariable String candidateId) {
        return handle(() -> candidateSummary(pipelineService.startRound3(candidateId)));
    }

    @PostMapping("/candidates/{candidateId}/round3/feedback")
    @PreAuthorize("hasAnyRole('" + MANAGER_ROLES + "')")
    public ResponseEntity<?> round3Feedback(@PathVariable String candidateId, @Valid @RequestBody Round3FeedbackRequest req,
                                            @RequestHeader("X-User-Id") String userId,
                                            @RequestHeader("X-User-Role") String userRole) {
        try {
            return ResponseEntity.ok(candidateSummary(pipelineService.submitRound3Feedback(candidateId, req, userId, userRole)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "error", e.getMessage()));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to finalize Round 3 for candidate {}", candidateId, e);
            return ResponseEntity.status(500).body(Map.of("ok", false, "error", "Failed to onboard candidate: " + e.getMessage()));
        }
    }

    private ResponseEntity<?> handle(java.util.function.Supplier<?> action) {
        try {
            return ResponseEntity.ok(action.get());
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "error", e.getMessage()));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    private Map<String, Object> batchSummary(ScreeningBatch b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("language", b.getLanguage());
        m.put("deadline", b.getDeadline().toString());
        m.put("status", b.getStatus().name());
        m.put("assignerEmail", b.getAssignerEmail());
        m.put("createdAt", b.getCreatedAt().toString());
        return m;
    }

    private Map<String, Object> candidateSummary(ScreeningCandidate c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("email", c.getEmail());
        m.put("stage", c.getStage().name());
        m.put("round1Link", mailService.buildCandidateLink(c));
        m.put("contactNumber", c.getContactNumber());
        m.put("institute", c.getInstitute());
        m.put("branch", c.getBranch());
        m.put("yop", c.getYop());
        m.put("experience", c.getExperience());
        m.put("round1Score", c.getRound1Score());
        m.put("round1Priority", c.getRound1Priority() != null ? c.getRound1Priority().name() : null);
        m.put("allowLateSubmission", c.isAllowLateSubmission());
        m.put("tabSwitchCount", c.getTabSwitchCount());
        m.put("proctoringViolation", c.isProctoringViolation());
        m.put("violationLocked", c.isViolationLocked());
        m.put("round2Strengths", c.getRound2Strengths());
        m.put("round2Weaknesses", c.getRound2Weaknesses());
        m.put("round2Practical", c.getRound2Practical());
        m.put("round2Improvements", c.getRound2Improvements());
        m.put("round2Marks", c.getRound2Marks());
        m.put("round2Result", c.getRound2Result() != null ? c.getRound2Result().name() : null);
        m.put("round3Communication", c.getRound3Communication());
        m.put("round3ProblemSolving", c.getRound3ProblemSolving());
        m.put("round3AttitudeCoachability", c.getRound3AttitudeCoachability());
        m.put("round3LearningAgility", c.getRound3LearningAgility());
        m.put("round3Teamwork", c.getRound3Teamwork());
        m.put("round3BodyLanguage", c.getRound3BodyLanguage());
        m.put("round3ConcludingComments", c.getRound3ConcludingComments());
        m.put("round3Total", c.getRound3Total());
        m.put("round3Result", c.getRound3Result() != null ? c.getRound3Result().name() : null);
        m.put("convertedUserId", c.getConvertedUserId());
        return m;
    }

    private Map<String, Object> answerDetail(ScreeningAnswer a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("questionId", a.getQuestion().getId());
        m.put("questionType", a.getQuestion().getQuestionType().name());
        m.put("prompt", a.getQuestion().getPrompt());
        m.put("marks", a.getQuestion().getMarks());
        m.put("referenceAnswer", a.getQuestion().getReferenceAnswer());
        m.put("rawAnswer", a.getRawAnswer());
        m.put("score", a.getScore());
        m.put("aiFeedback", a.getAiFeedback());
        return m;
    }
}
