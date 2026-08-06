package com.benchreadiness.ops.compliance.controller;

import com.benchreadiness.ops.compliance.entity.TokenUsage;
import com.benchreadiness.ops.security.StaffSecurityRoles;
import com.benchreadiness.ops.compliance.service.TokenTrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/tokens")
public class TokenTrackingController {

    private static final Logger log = LoggerFactory.getLogger(TokenTrackingController.class);

    private final TokenTrackingService tokenTrackingService;

    public TokenTrackingController(TokenTrackingService tokenTrackingService) {
        this.tokenTrackingService = tokenTrackingService;
    }

    @PostMapping("/track")
    public ResponseEntity<Void> trackUsage(@RequestBody Map<String, Object> request,
                                           @RequestHeader("X-User-Id") String userId) {
        String interviewId = (String) request.get("interviewId");
        String operationType = (String) request.get("operationType");
        String modelUsed = (String) request.get("modelUsed");
        Integer promptTokens = (Integer) request.get("promptTokens");
        Integer completionTokens = (Integer) request.get("completionTokens");

        if (interviewId == null || interviewId.isBlank() || operationType == null
                || modelUsed == null || promptTokens == null || completionTokens == null) {
            return ResponseEntity.badRequest().build();
        }

        tokenTrackingService.trackTokenUsage(interviewId, operationType, modelUsed,
            promptTokens, completionTokens, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/check-limit")
    public ResponseEntity<Map<String, Object>> checkDailyLimit(@RequestHeader("X-User-Id") String userId) {
        boolean canProceed = tokenTrackingService.checkDailyLimit(userId);
        Map<String, Object> status = tokenTrackingService.getDailyUsageStatus(userId);
        status.put("canProceed", canProceed);
        return ResponseEntity.ok(status);
    }

    @GetMapping("/usage/{interviewId}")
    public ResponseEntity<List<TokenUsage>> getInterviewUsage(@PathVariable String interviewId) {
        return ResponseEntity.ok(tokenTrackingService.getInterviewTokenUsage(interviewId));
    }

    @GetMapping("/analytics/daily")
    public ResponseEntity<Map<String, Object>> getDailyAnalytics() {
        return ResponseEntity.ok(tokenTrackingService.getDailyAnalytics());
    }

    @PostMapping("/limits")
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.ADMIN + "')")
    public ResponseEntity<Map<String, Object>> updateLimits(@RequestBody Map<String, Integer> request) {
        try {
            Integer dailyLimit = request.get("dailyLimit");
            Integer warningThreshold = request.getOrDefault("warningThreshold",
                dailyLimit != null ? (int) (dailyLimit * 0.8) : 80000);
            tokenTrackingService.updateDailyLimit(dailyLimit, warningThreshold);
            return ResponseEntity.ok(Map.of("success", true, "message", "Token limits updated successfully",
                "dailyLimit", dailyLimit, "warningThreshold", warningThreshold));
        } catch (Exception e) {
            log.error("Error updating limits", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/assessment-response")
    public ResponseEntity<Void> storeAssessmentResponse(@RequestBody Map<String, Object> request,
                                                        @RequestHeader("X-User-Id") String userId) {
        tokenTrackingService.storeAssessmentResponse(
            (String) request.get("interviewId"),
            (String) request.get("assessmentJson"),
            (Integer) request.get("tokensUsed"),
            (String) request.get("assessmentSource")
        );
        return ResponseEntity.ok().build();
    }

    @GetMapping("/assessment-response/{interviewId}")
    public ResponseEntity<Map<String, Object>> getAssessmentResponse(@PathVariable String interviewId) {
        return ResponseEntity.ok(tokenTrackingService.getAssessmentResponse(interviewId));
    }

    @GetMapping("/interview-summary/{interviewId}")
    public ResponseEntity<Map<String, Object>> getInterviewTokenSummary(@PathVariable String interviewId) {
        return ResponseEntity.ok(tokenTrackingService.getInterviewTokenSummary(interviewId));
    }

    @PostMapping("/finalize-interview")
    public ResponseEntity<Void> finalizeInterviewTokens(@RequestBody Map<String, Object> request) {
        tokenTrackingService.finalizeInterviewTokenSummary((String) request.get("interviewId"));
        return ResponseEntity.ok().build();
    }

    // ── F4: Rubric cache endpoints ────────────────────────────────────────────

    @GetMapping("/rubric-cache/{cacheKey}")
    public ResponseEntity<Map<String, Object>> getRubricCache(@PathVariable String cacheKey) {
        return tokenTrackingService.getRubricCache(cacheKey)
                .map(json -> ResponseEntity.ok(Map.<String, Object>of("cacheKey", cacheKey, "rubricJson", json)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/rubric-cache")
    public ResponseEntity<Void> storeRubricCache(@RequestBody Map<String, String> request) {
        String cacheKey = request.get("cacheKey");
        String rubricJson = request.get("rubricJson");
        if (cacheKey == null || rubricJson == null) {
            return ResponseEntity.badRequest().build();
        }
        tokenTrackingService.storeRubricCache(cacheKey, rubricJson);
        return ResponseEntity.ok().build();
    }
}
