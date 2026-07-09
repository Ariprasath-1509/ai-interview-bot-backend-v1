package com.benchreadiness.screening.controller;

import com.benchreadiness.screening.dto.LockTestRequest;
import com.benchreadiness.screening.dto.SubmitAnswersRequest;
import com.benchreadiness.screening.service.CandidateTestService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.NoSuchElementException;

/** No authentication — possession of the single-use token is the only credential, matching the "not the same login" requirement. */
@RestController
@RequestMapping("/screening/public")
public class ScreeningPublicController {

    private static final Logger log = LoggerFactory.getLogger(ScreeningPublicController.class);

    private final CandidateTestService candidateTestService;

    public ScreeningPublicController(CandidateTestService candidateTestService) {
        this.candidateTestService = candidateTestService;
    }

    @GetMapping("/{token}")
    public ResponseEntity<?> getTest(@PathVariable String token) {
        try {
            return ResponseEntity.ok(candidateTestService.getView(token));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(410).body(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/{token}/submit")
    public ResponseEntity<?> submit(@PathVariable String token, @Valid @RequestBody SubmitAnswersRequest req) {
        try {
            return ResponseEntity.ok(candidateTestService.submitAnswers(token, req));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(410).body(Map.of("ok", false, "error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to grade submission for token {}", token, e);
            return ResponseEntity.status(500).body(Map.of("ok", false, "error", "Failed to submit — please try again"));
        }
    }

    /** Pauses the test on a 2nd proctoring violation instead of submitting — staff must permit the candidate to resume. */
    @PostMapping("/{token}/lock")
    public ResponseEntity<?> lock(@PathVariable String token, @RequestBody(required = false) LockTestRequest req) {
        try {
            candidateTestService.lockForViolation(token, req != null ? req.getTabSwitchCount() : null);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "error", e.getMessage()));
        }
    }
}
