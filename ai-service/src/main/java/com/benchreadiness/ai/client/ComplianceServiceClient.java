package com.benchreadiness.ai.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "compliance-service")
public interface ComplianceServiceClient {

    @PostMapping("/tokens/track")
    void trackTokenUsage(@RequestBody Map<String, Object> request,
                        @RequestHeader("X-User-Id") String userId);

    @PostMapping("/tokens/assessment-response")
    void storeAssessmentResponse(@RequestBody Map<String, Object> request,
                               @RequestHeader("X-User-Id") String userId);

    @PostMapping("/tokens/finalize-interview")
    void finalizeInterviewTokens(@RequestBody Map<String, String> request,
                               @RequestHeader("X-User-Id") String userId);

    @GetMapping("/tokens/interview-summary/{interviewId}")
    Map<String, Object> getInterviewTokenSummary(@PathVariable("interviewId") String interviewId);

    @GetMapping("/tokens/usage/{interviewId}")
    List<Map<String, Object>> getInterviewUsage(@PathVariable("interviewId") String interviewId);
}