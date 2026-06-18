package com.benchreadiness.ai.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "interview-service")
public interface InterviewServiceClient {

    @PutMapping("/interviews/{id}/assessment-status")
    void updateAssessmentStatus(@PathVariable("id") String interviewId,
                                @RequestBody Map<String, Object> body);

    @GetMapping("/interviews/{id}")
    Map<String, Object> getInterview(@PathVariable("id") String interviewId);
}
