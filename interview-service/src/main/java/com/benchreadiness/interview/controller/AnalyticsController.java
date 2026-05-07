package com.benchreadiness.interview.controller;

import com.benchreadiness.interview.service.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/analytics")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/realtime")
    public ResponseEntity<Map<String, Object>> getRealTimeAnalytics(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        return ResponseEntity.ok(analyticsService.getRealTimeAnalytics(userId, userRole));
    }

    @GetMapping("/modes")
    public ResponseEntity<Map<String, Object>> getInterviewModeAnalytics(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        return ResponseEntity.ok(analyticsService.getInterviewModeAnalytics(userId, userRole));
    }

    @GetMapping("/verdicts")
    public ResponseEntity<Map<String, Object>> getVerdictAnalytics(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        return ResponseEntity.ok(analyticsService.getVerdictAnalytics(userId, userRole));
    }

    @GetMapping("/interviewers")
    public ResponseEntity<Map<String, Object>> getInterviewerAnalytics(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        return ResponseEntity.ok(analyticsService.getInterviewerAnalytics(userId, userRole));
    }

    @GetMapping("/trends")
    public ResponseEntity<Map<String, Object>> getTrendAnalytics(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        return ResponseEntity.ok(analyticsService.getTrendAnalytics(userId, userRole));
    }

    @GetMapping("/candidates")
    public ResponseEntity<Map<String, Object>> getCandidatePerformanceAnalytics(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        return ResponseEntity.ok(analyticsService.getCandidatePerformanceAnalytics(userId, userRole));
    }

    @GetMapping("/debug")
    public ResponseEntity<Map<String, Object>> getDebugInfo(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        return ResponseEntity.ok(analyticsService.getDebugInfo(userId, userRole));
    }
}
