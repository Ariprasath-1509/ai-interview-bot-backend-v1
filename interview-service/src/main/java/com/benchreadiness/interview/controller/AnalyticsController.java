package com.benchreadiness.interview.controller;

import com.benchreadiness.interview.service.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/realtime")
    public ResponseEntity<Map<String, Object>> getRealTimeAnalytics(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        Map<String, Object> analytics = analyticsService.getRealTimeAnalytics(userId, userRole);
        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/modes")
    public ResponseEntity<Map<String, Object>> getInterviewModeAnalytics(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        Map<String, Object> analytics = analyticsService.getInterviewModeAnalytics(userId, userRole);
        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/verdicts")
    public ResponseEntity<Map<String, Object>> getVerdictAnalytics(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        Map<String, Object> analytics = analyticsService.getVerdictAnalytics(userId, userRole);
        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/interviewers")
    public ResponseEntity<Map<String, Object>> getInterviewerAnalytics(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        Map<String, Object> analytics = analyticsService.getInterviewerAnalytics(userId, userRole);
        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/trends")
    public ResponseEntity<Map<String, Object>> getTrendAnalytics(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        Map<String, Object> analytics = analyticsService.getTrendAnalytics(userId, userRole);
        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/debug")
    public ResponseEntity<Map<String, Object>> getDebugInfo(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        Map<String, Object> debug = analyticsService.getDebugInfo(userId, userRole);
        return ResponseEntity.ok(debug);
    }
}