package com.benchreadiness.compliance.controller;

import com.benchreadiness.compliance.entity.TokenUsage;
import com.benchreadiness.compliance.service.TokenTrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
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
        log.info(">>> COMPLIANCE: Received /tokens/track request from userId: {}", userId);
        log.info(">>> COMPLIANCE: Request body: {}", request);
        
        String interviewId = (String) request.get("interviewId");
        String operationType = (String) request.get("operationType");
        String modelUsed = (String) request.get("modelUsed");
        Integer promptTokens = (Integer) request.get("promptTokens");
        Integer completionTokens = (Integer) request.get("completionTokens");
        
        if (interviewId == null || interviewId.isBlank()) {
            log.warn("<<< COMPLIANCE: Missing interviewId in token tracking request");
            return ResponseEntity.badRequest().build();
        }
        
        if (operationType == null || modelUsed == null || promptTokens == null || completionTokens == null) {
            log.warn("<<< COMPLIANCE: Missing required fields - operationType: {}, modelUsed: {}, promptTokens: {}, completionTokens: {}",
                    operationType, modelUsed, promptTokens, completionTokens);
            return ResponseEntity.badRequest().build();
        }
        
        tokenTrackingService.trackTokenUsage(interviewId, operationType, modelUsed, 
                                           promptTokens, completionTokens, userId);
        log.info("<<< COMPLIANCE: Successfully tracked {} total tokens for interview {} ({})", 
                promptTokens + completionTokens, interviewId, operationType);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/check-limit")
    public ResponseEntity<Map<String, Object>> checkDailyLimit(@RequestHeader("X-User-Id") String userId) {
        log.info(">>> COMPLIANCE: Received /tokens/check-limit request from userId: {}", userId);
        
        boolean canProceed = tokenTrackingService.checkDailyLimit(userId);
        Map<String, Object> status = tokenTrackingService.getDailyUsageStatus(userId);
        status.put("canProceed", canProceed);
        
        log.info("<<< COMPLIANCE: Returning check-limit status: {}", status);
        return ResponseEntity.ok(status);
    }

    @GetMapping("/usage/{interviewId}")
    public ResponseEntity<List<TokenUsage>> getInterviewUsage(@PathVariable String interviewId) {
        log.info(">>> COMPLIANCE: Received /tokens/usage/{} request", interviewId);
        List<TokenUsage> usage = tokenTrackingService.getInterviewTokenUsage(interviewId);
        log.info("<<< COMPLIANCE: Returning {} usage records", usage.size());
        return ResponseEntity.ok(usage);
    }

    @GetMapping("/analytics/daily")
    public ResponseEntity<Map<String, Object>> getDailyAnalytics() {
        log.info(">>> COMPLIANCE: Received /tokens/analytics/daily request");
        Map<String, Object> analytics = tokenTrackingService.getDailyAnalytics();
        log.info("<<< COMPLIANCE: Returning daily analytics: {}", analytics);
        return ResponseEntity.ok(analytics);
    }

    @PostMapping("/limits")
    public ResponseEntity<Map<String, Object>> updateLimits(@RequestBody Map<String, Integer> request) {
        log.info(">>> COMPLIANCE: Received /tokens/limits POST request");
        log.info(">>> COMPLIANCE: Request body: {}", request);
        
        try {
            Integer dailyLimit = request.get("dailyLimit");
            Integer warningThreshold = request.getOrDefault("warningThreshold", dailyLimit != null ? (int)(dailyLimit * 0.8) : 80000);
            
            log.info(">>> COMPLIANCE: Updating limits - dailyLimit: {}, warningThreshold: {}", dailyLimit, warningThreshold);
            tokenTrackingService.updateDailyLimit(dailyLimit, warningThreshold);
            
            // Return the updated limits in response
            Map<String, Object> response = Map.of(
                "success", true,
                "message", "Token limits updated successfully",
                "dailyLimit", dailyLimit,
                "warningThreshold", warningThreshold
            );
            
            log.info("<<< COMPLIANCE: Successfully updated limits, returning response: {}", response);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("<<< COMPLIANCE: Error updating limits", e);
            Map<String, Object> errorResponse = Map.of(
                "success", false,
                "message", "Failed to update token limits: " + e.getMessage()
            );
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    @PostMapping("/assessment-response")
    public ResponseEntity<Void> storeAssessmentResponse(@RequestBody Map<String, Object> request,
                                                       @RequestHeader("X-User-Id") String userId) {
        log.info(">>> COMPLIANCE: Received /tokens/assessment-response request from userId: {}", userId);
        
        String interviewId = (String) request.get("interviewId");
        String assessmentJson = (String) request.get("assessmentJson");
        Integer tokensUsed = (Integer) request.get("tokensUsed");
        String assessmentSource = (String) request.get("assessmentSource");
        
        tokenTrackingService.storeAssessmentResponse(interviewId, assessmentJson, tokensUsed, assessmentSource);
        log.info("<<< COMPLIANCE: Successfully stored assessment response");
        return ResponseEntity.ok().build();
    }

    @GetMapping("/assessment-response/{interviewId}")
    public ResponseEntity<Map<String, Object>> getAssessmentResponse(@PathVariable String interviewId) {
        log.info(">>> COMPLIANCE: Received /tokens/assessment-response/{} request", interviewId);
        Map<String, Object> response = tokenTrackingService.getAssessmentResponse(interviewId);
        log.info("<<< COMPLIANCE: Returning assessment response");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/interview-summary/{interviewId}")
    public ResponseEntity<Map<String, Object>> getInterviewTokenSummary(@PathVariable String interviewId) {
        log.info(">>> COMPLIANCE: Received /tokens/interview-summary/{} request", interviewId);
        Map<String, Object> summary = tokenTrackingService.getInterviewTokenSummary(interviewId);
        log.info("<<< COMPLIANCE: Returning interview token summary: {}", summary);
        return ResponseEntity.ok(summary);
    }

    @PostMapping("/finalize-interview")
    public ResponseEntity<Void> finalizeInterviewTokens(@RequestBody Map<String, Object> request) {
        log.info(">>> COMPLIANCE: Received /tokens/finalize-interview request");
        String interviewId = (String) request.get("interviewId");
        tokenTrackingService.finalizeInterviewTokenSummary(interviewId);
        log.info("<<< COMPLIANCE: Successfully finalized interview tokens");
        return ResponseEntity.ok().build();
    }
}
