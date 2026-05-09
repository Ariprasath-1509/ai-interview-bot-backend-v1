package com.benchreadiness.interview.controller;

import com.benchreadiness.interview.dto.SkillBasedMatchingResult;
import com.benchreadiness.interview.service.SkillBasedMatchingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/clients/skill-matching")
public class SkillBasedMatchingController {
    
    private final SkillBasedMatchingService skillBasedMatchingService;
    
    public SkillBasedMatchingController(SkillBasedMatchingService skillBasedMatchingService) {
        this.skillBasedMatchingService = skillBasedMatchingService;
    }
    
    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<List<SkillBasedMatchingResult>> getSkillMatchingOverview(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        
        List<SkillBasedMatchingResult> results = skillBasedMatchingService.getSkillMatchingOverview(userId, userRole);
        return ResponseEntity.ok(results);
    }
    
    @GetMapping("/{clientId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<SkillBasedMatchingResult> getSkillBasedMatches(
            @PathVariable UUID clientId,
            @RequestParam String source,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        
        SkillBasedMatchingResult result = skillBasedMatchingService.getSkillBasedMatches(
            clientId.toString(), source, userId, userRole);
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/{clientId}/refresh")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<SkillBasedMatchingResult> refreshSkillBasedMatches(
            @PathVariable UUID clientId,
            @RequestParam String source,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        
        SkillBasedMatchingResult result = skillBasedMatchingService.refreshSkillBasedMatches(
            clientId.toString(), source, userId, userRole);
        return ResponseEntity.ok(result);
    }
}