package com.benchreadiness.ops.observer.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;

@FeignClient(name = "interview-service", contextId = "observerInterviewServiceClient")
public interface InterviewServiceClient {

    @GetMapping("/interviews/{id}")
    Map<String, Object> getInterview(@PathVariable("id") String id);

    @GetMapping("/interviews/today")
    List<Map<String, Object>> getTodaysInterviews();

    @GetMapping("/analytics/daily-report")
    Map<String, Object> getDailyReportData();
}
