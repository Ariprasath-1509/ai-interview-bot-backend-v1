package com.benchreadiness.interview.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "ops-service", contextId = "complianceServiceClient", configuration = com.benchreadiness.interview.config.FeignConfig.class)
public interface ComplianceServiceClient {

    @GetMapping("/tokens/check-limit")
    Map<String, Object> checkTokenLimit(@RequestHeader("X-User-Id") String userId);

    @PostMapping("/tokens/track")
    void trackTokenUsage(@RequestBody Map<String, Object> request,
                        @RequestHeader("X-User-Id") String userId);

    @PostMapping("/tokens/assessment-response")
    void storeAssessmentResponse(@RequestBody Map<String, Object> request,
                               @RequestHeader("X-User-Id") String userId);

    @GetMapping("/tokens/assessment-response/{interviewId}")
    Map<String, Object> getAssessmentResponse(@PathVariable("interviewId") String interviewId,
                                            @RequestHeader("X-User-Id") String userId);

    @PostMapping("/tokens/finalize-interview")
    void finalizeInterviewTokens(@RequestBody Map<String, String> request,
                               @RequestHeader("X-User-Id") String userId);

    @PostMapping("/compliance/audit-logs")
    void recordAuditLog(@RequestBody Map<String, Object> auditLog);

    @GetMapping("/tokens/analytics/per-interview")
    Map<String, Object> getPerInterviewTokenAnalytics();

    @GetMapping("/tokens/analytics/range")
    Map<String, Object> getTokenAnalyticsForRange(@RequestParam("from") String from,
                                                   @RequestParam("to") String to);
}