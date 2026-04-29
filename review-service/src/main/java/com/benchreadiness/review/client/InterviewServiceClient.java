package com.benchreadiness.review.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "interview-service")
public interface InterviewServiceClient {
    
    @PatchMapping("/interviews/{id}")
    void updateInterview(@PathVariable("id") String id, @RequestBody Map<String, Object> updates);
}