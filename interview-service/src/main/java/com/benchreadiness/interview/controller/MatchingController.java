package com.benchreadiness.interview.controller;

import com.benchreadiness.interview.dto.CandidateMatch;
import com.benchreadiness.interview.dto.MatchingRequest;
import com.benchreadiness.interview.security.StaffSecurityRoles;
import com.benchreadiness.interview.service.MatchingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ + "')")
    public ResponseEntity<?> findMatchingCandidates(@Valid @RequestBody MatchingRequest req,
                                                   @RequestHeader("X-User-Role") String userRole,
                                                   @RequestHeader("X-User-Id") String userId) {
        try {
            List<CandidateMatch> matches = matchingService.findMatchingCandidates(
                req.getClientId(), req.getSource(), req.getMaxCandidates(), 
                req.getSkillSet(), req.getMinYoeRequired(), userId, userRole
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
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ + "')")
    public ResponseEntity<?> findBenchCandidates(@PathVariable String clientId,
                                                @RequestParam(defaultValue = "10") Integer maxCandidates,
                                                @RequestHeader("X-User-Role") String userRole,
                                                @RequestHeader("X-User-Id") String userId) {
        try {
            List<CandidateMatch> matches = matchingService.findMatchingCandidates(
                clientId, "BENCH_B2B", maxCandidates, null, null, userId, userRole
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
    @PreAuthorize("hasAnyRole('" + StaffSecurityRoles.READ + "')")
    public ResponseEntity<?> findMarketCandidates(@PathVariable String clientId,
                                                 @RequestParam(defaultValue = "10") Integer maxCandidates,
                                                 @RequestHeader("X-User-Role") String userRole,
                                                 @RequestHeader("X-User-Id") String userId) {
        try {
            List<CandidateMatch> matches = matchingService.findMatchingCandidates(
                clientId, "MARKET", maxCandidates, null, null, userId, userRole
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
