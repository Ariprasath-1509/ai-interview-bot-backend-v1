package com.benchreadiness.compliance.controller;

import com.benchreadiness.compliance.dto.AuditLogRequest;
import com.benchreadiness.compliance.entity.AuditLog;
import com.benchreadiness.compliance.entity.RetentionPolicy;
import com.benchreadiness.compliance.service.ComplianceService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/compliance")
public class ComplianceController {

    private final ComplianceService complianceService;

    public ComplianceController(ComplianceService complianceService) {
        this.complianceService = complianceService;
    }

    /** GET /compliance/audit-logs — paginated audit log */
    @GetMapping("/audit-logs")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestHeader("X-User-Role") String role) {
        Page<AuditLog> logs = complianceService.getAuditLogs(page, size);
        return ResponseEntity.ok(Map.of(
            "content", logs.getContent(),
            "totalElements", logs.getTotalElements(),
            "totalPages", logs.getTotalPages(),
            "page", page
        ));
    }

    /** GET /compliance/audit-logs/actor/{actorId} — logs by actor */
    @GetMapping("/audit-logs/actor/{actorId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> getLogsByActor(@PathVariable String actorId,
                                             @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(complianceService.getLogsByActor(actorId));
    }

    /** GET /compliance/audit-logs/resource/{resourceId} — logs by resource (e.g. interview id) */
    @GetMapping("/audit-logs/resource/{resourceId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> getLogsByResource(@PathVariable String resourceId,
                                                @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(complianceService.getLogsByResource(resourceId));
    }

    /** POST /compliance/audit-logs — internal endpoint for other services to record events */
    @PostMapping("/audit-logs")
    public ResponseEntity<AuditLog> record(@Valid @RequestBody AuditLogRequest req) {
        return ResponseEntity.ok(complianceService.record(req));
    }

    /** GET /compliance/retention-policies — list all retention policies */
    @GetMapping("/retention-policies")
    public ResponseEntity<List<RetentionPolicy>> getRetentionPolicies(
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(complianceService.getRetentionPolicies());
    }

    /** PUT /compliance/retention-policies/{region} — update retention policy for a region */
    @PutMapping("/retention-policies/{region}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> upsertRetentionPolicy(
            @PathVariable String region,
            @RequestBody Map<String, Integer> body,
            @RequestHeader("X-User-Role") String role) {
        int transcriptDays = body.getOrDefault("transcriptDays", 365);
        int audioDays = body.getOrDefault("audioDays", 90);
        return ResponseEntity.ok(complianceService.upsertRetentionPolicy(region, transcriptDays, audioDays));
    }
}
