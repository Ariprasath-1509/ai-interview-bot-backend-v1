package com.benchreadiness.interview.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "api-gateway-ai", url = "http://localhost:6002", configuration = com.benchreadiness.interview.config.FeignConfig.class)
public interface AiServiceClient {

    @PostMapping("/ai/generate-rubric")
    String generateRubric(@RequestBody Map<String, String> request);

    @PostMapping("/ai/next-question")
    Map<String, Object> getNextQuestion(@RequestBody Map<String, Object> request);

    @PostMapping("/ai/assess")
    String assessInterview(@RequestBody Map<String, Object> request);

    @PostMapping("/ai/resume-summary")
    Map<String, Object> generateResumeSummary(@RequestBody Map<String, Object> request);
}