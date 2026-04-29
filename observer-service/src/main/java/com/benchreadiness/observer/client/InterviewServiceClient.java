package com.benchreadiness.observer.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "interview-service")
public interface InterviewServiceClient {
    
    @GetMapping("/interviews/{id}")
    Map<String, Object> getInterview(@PathVariable("id") String id);
}