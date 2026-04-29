package com.benchreadiness.interview.controller;

import com.benchreadiness.interview.dto.CompleteInterviewRequest;
import com.benchreadiness.interview.dto.CreateInterviewRequest;
import com.benchreadiness.interview.entity.Interview;
import com.benchreadiness.interview.entity.InterviewPlan;
import com.benchreadiness.interview.entity.JobDescription;
import com.benchreadiness.interview.service.InterviewService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/interviews")
public class InterviewController {

    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateInterviewRequest req,
                                     @RequestHeader("X-User-Id") String userId) {
        try {
            Interview interview = interviewService.createInterview(req, userId);
            return ResponseEntity.ok(Map.of("id", interview.getId(), "status", interview.getStatus()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id,
                                   @RequestHeader("X-User-Role") String userRole) {
        // Only BENCH_MANAGER can delete interviews
        if (!"BENCH_MANAGER".equals(userRole)) {
            return ResponseEntity.status(403).body(Map.of("error", "Only bench managers can delete interviews"));
        }
        
        try {
            boolean deleted = interviewService.deleteInterview(id);
            if (deleted) {
                return ResponseEntity.ok(Map.of("message", "Interview deleted successfully"));
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable String id) {
        return interviewService.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<Interview>> getAll() {
        return ResponseEntity.ok(interviewService.findAll());
    }

    @GetMapping("/today")
    public ResponseEntity<?> getToday() {
        return ResponseEntity.ok(interviewService.getTodaysSummaries());
    }

    @GetMapping("/summary")
    public ResponseEntity<List<com.benchreadiness.interview.dto.InterviewSummaryDto>> getSummary() {
        return ResponseEntity.ok(interviewService.getSummaries());
    }

    @GetMapping("/mine")
    public ResponseEntity<List<Interview>> getMine(@RequestHeader("X-User-Email") String email) {
        return ResponseEntity.ok(interviewService.findByEmail(email));
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<?> complete(@PathVariable String id,
                                       @RequestBody CompleteInterviewRequest req) {
        try {
            Interview updated = interviewService.completeInterview(id, req);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/abandon")
    public ResponseEntity<?> abandon(@PathVariable String id,
                                      @RequestBody com.benchreadiness.interview.dto.AbandonInterviewRequest req) {
        try {
            Interview updated = interviewService.abandonInterview(id, req);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/jd/{jdId}")
    public ResponseEntity<?> getJd(@PathVariable String jdId) {
        return interviewService.findJdById(jdId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/plans/{planId}")
    public ResponseEntity<?> getPlan(@PathVariable String planId) {
        return interviewService.findPlanById(planId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
