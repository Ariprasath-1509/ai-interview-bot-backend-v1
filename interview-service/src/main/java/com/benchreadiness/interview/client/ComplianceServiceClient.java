package com.benchreadiness.interview.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "compliance-service")
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
}