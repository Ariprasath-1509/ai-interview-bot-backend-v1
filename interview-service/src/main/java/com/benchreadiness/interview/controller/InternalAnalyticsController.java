package com.benchreadiness.interview.controller;

import com.benchreadiness.interview.service.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Service-to-service endpoints for scheduled digest (gateway shared key). */
@RestController
@RequestMapping("/analytics/internal")
public class InternalAnalyticsController {

    private final AnalyticsService analyticsService;

    public InternalAnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/daily-report")
    public ResponseEntity<Map<String, Object>> getDailyReportForDigest(
            @RequestParam(required = false) String branch) {
        return ResponseEntity.ok(analyticsService.getDailyReportDataForBranch(branch));
    }
}
