package com.benchreadiness.interview.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "ai-service")
public interface AiServiceClient {

    @PostMapping("/ai/generate-rubric")
    String generateRubric(@RequestBody Map<String, String> request,
                         @RequestHeader("X-User-Id") String userId,
                         @RequestHeader("X-Interview-Id") String interviewId);

    @PostMapping("/ai/next-question")
    Map<String, Object> getNextQuestion(@RequestBody Map<String, Object> request,
                                       @RequestHeader("X-User-Id") String userId);

    @PostMapping("/ai/assess")
    String assessInterview(@RequestBody Map<String, Object> request,
                          @RequestHeader("X-User-Id") String userId);
}