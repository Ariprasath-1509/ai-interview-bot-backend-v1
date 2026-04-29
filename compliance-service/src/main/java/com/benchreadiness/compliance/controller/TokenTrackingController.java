package com.benchreadiness.compliance.controller;

import com.benchreadiness.compliance.entity.TokenUsage;
import com.benchreadiness.compliance.service.TokenTrackingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/tokens")
public class TokenTrackingController {

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
        List<TokenUsage> usage = tokenTrackingService.getInterviewTokenUsage(interviewId);
        return ResponseEntity.ok(usage);
    }

    @GetMapping("/analytics/daily")
    public ResponseEntity<Map<String, Object>> getDailyAnalytics() {
        Map<String, Object> analytics = tokenTrackingService.getDailyAnalytics();
        return ResponseEntity.ok(analytics);
    }

    @PostMapping("/limits")
    public ResponseEntity<Void> updateLimits(@RequestBody Map<String, Integer> request) {
        Integer dailyLimit = request.get("dailyLimit");
        Integer warningThreshold = request.get("warningThreshold");
        tokenTrackingService.updateDailyLimit(dailyLimit, warningThreshold);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/assessment-response")
    public ResponseEntity<Void> storeAssessmentResponse(@RequestBody Map<String, Object> request,
                                                       @RequestHeader("X-User-Id") String userId) {
        String interviewId = (String) request.get("interviewId");
        String assessmentJson = (String) request.get("assessmentJson");
        Integer tokensUsed = (Integer) request.get("tokensUsed");
        String assessmentSource = (String) request.get("assessmentSource");
        
        tokenTrackingService.storeAssessmentResponse(interviewId, assessmentJson, tokensUsed, assessmentSource);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/assessment-response/{interviewId}")
    public ResponseEntity<Map<String, Object>> getAssessmentResponse(@PathVariable String interviewId) {
        Map<String, Object> response = tokenTrackingService.getAssessmentResponse(interviewId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/interview-summary/{interviewId}")
    public ResponseEntity<Map<String, Object>> getInterviewTokenSummary(@PathVariable String interviewId) {
        Map<String, Object> summary = tokenTrackingService.getInterviewTokenSummary(interviewId);
        return ResponseEntity.ok(summary);
    }

    @PostMapping("/finalize-interview")
    public ResponseEntity<Void> finalizeInterviewTokens(@RequestBody Map<String, Object> request) {
        String interviewId = (String) request.get("interviewId");
        tokenTrackingService.finalizeInterviewTokenSummary(interviewId);
        return ResponseEntity.ok().build();
    }
}