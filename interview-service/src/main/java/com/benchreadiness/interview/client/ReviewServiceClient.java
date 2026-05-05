package com.benchreadiness.interview.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "review-service", configuration = com.benchreadiness.interview.config.FeignConfig.class)
public interface ReviewServiceClient {

    @GetMapping("/scores/{interviewId}")
    List<Map<String, Object>> getScores(@PathVariable("interviewId") String interviewId);

    @PostMapping("/scores")
    void saveScores(@RequestBody Map<String, Object> request);

    @GetMapping("/reviews/{interviewId}")
    Map<String, Object> getReview(@PathVariable("interviewId") String interviewId);

    @PostMapping("/reviews/{interviewId}/sign-off")
    Map<String, Object> signOff(@PathVariable("interviewId") String interviewId,
                               @RequestBody Map<String, Object> request,
                               @RequestHeader("X-User-Id") String userId,
                               @RequestHeader("X-User-Role") String userRole);
}