package com.benchreadiness.interview.controller;

import com.benchreadiness.interview.dto.CandidateMatch;
import com.benchreadiness.interview.dto.MatchingRequest;
import com.benchreadiness.interview.service.MatchingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/matching")
public class MatchingController {
    
    private final MatchingService matchingService;
    
    public MatchingController(MatchingService matchingService) {
        this.matchingService = matchingService;
    }
    
    @PostMapping("/candidates")
    public ResponseEntity<?> findMatchingCandidates(@Valid @RequestBody MatchingRequest req,
                                                   @RequestHeader("X-User-Role") String userRole,
                                                   @RequestHeader("X-User-Id") String userId) {
        // ADMIN, SUPER_ADMIN, and RECRUITER can trigger matching
        if (!"ADMIN".equals(userRole) && !"SUPER_ADMIN".equals(userRole) && !"RECRUITER".equals(userRole)) {
            return ResponseEntity.status(403).body(Map.of("error", "Only admins and recruiters can trigger candidate matching"));
        }
        
        try {
            List<CandidateMatch> matches = matchingService.findMatchingCandidates(
                req.getClientId(), 
                req.getSource(), 
                req.getMaxCandidates(),
                userId,
                userRole
            );
            
            return ResponseEntity.ok(Map.of(
                "matches", matches,
                "totalFound", matches.size(),
                "clientId", req.getClientId(),
                "source", req.getSource()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @PostMapping("/clients/{clientId}/bench-candidates")
    public ResponseEntity<?> findBenchCandidates(@PathVariable String clientId,
                                                @RequestParam(defaultValue = "10") Integer maxCandidates,
                                                @RequestHeader("X-User-Role") String userRole,
                                                @RequestHeader("X-User-Id") String userId) {
        if (!"ADMIN".equals(userRole) && !"SUPER_ADMIN".equals(userRole) && !"RECRUITER".equals(userRole)) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }
        
        try {
            List<CandidateMatch> matches = matchingService.findMatchingCandidates(
                clientId, "BENCH_B2B", maxCandidates, userId, userRole
            );
            
            return ResponseEntity.ok(Map.of(
                "matches", matches,
                "totalFound", matches.size(),
                "source", "BENCH_B2B"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @PostMapping("/clients/{clientId}/market-candidates")
    public ResponseEntity<?> findMarketCandidates(@PathVariable String clientId,
                                                 @RequestParam(defaultValue = "10") Integer maxCandidates,
                                                 @RequestHeader("X-User-Role") String userRole,
                                                 @RequestHeader("X-User-Id") String userId) {
        if (!"ADMIN".equals(userRole) && !"SUPER_ADMIN".equals(userRole) && !"RECRUITER".equals(userRole)) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }
        
        try {
            List<CandidateMatch> matches = matchingService.findMatchingCandidates(
                clientId, "MARKET", maxCandidates, userId, userRole
            );
            
            return ResponseEntity.ok(Map.of(
                "matches", matches,
                "totalFound", matches.size(),
                "source", "MARKET"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}