package com.benchreadiness.interview.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@FeignClient(name = "ai-service", url = "${ai-service.url:}")
public interface AiMatchingClient {
    
    @PostMapping("/ai/match-candidates")
    Map<String, Object> matchCandidates(@RequestBody Map<String, Object> request,
                                       @RequestHeader("X-User-Id") String userId);
}