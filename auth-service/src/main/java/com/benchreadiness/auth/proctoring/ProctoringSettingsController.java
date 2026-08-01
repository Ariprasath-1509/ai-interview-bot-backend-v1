package com.benchreadiness.auth.proctoring;

import com.benchreadiness.auth.proctoring.dto.UpdateProctoringSettingsRequest;
import com.benchreadiness.auth.service.AuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class ProctoringSettingsController {

    private final ProctoringSettingsService proctoringSettingsService;
    private final AuditService auditService;

    public ProctoringSettingsController(ProctoringSettingsService proctoringSettingsService,
                                          AuditService auditService) {
        this.proctoringSettingsService = proctoringSettingsService;
        this.auditService = auditService;
    }

    /** GET /auth/proctoring-settings — internal read for interview-service */
    @GetMapping("/auth/proctoring-settings")
    public ResponseEntity<Map<String, Object>> getSettings() {
        return ResponseEntity.ok(wrapSettings(proctoringSettingsService.getSettings()));
    }

    /** GET /auth/admin/proctoring-settings — admin UI (SUPER_ADMIN only: global platform settings) */
    @GetMapping("/auth/admin/proctoring-settings")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> getAdminSettings() {
        return ResponseEntity.ok(wrapSettings(proctoringSettingsService.getSettings()));
    }

    /** PUT /auth/admin/proctoring-settings — toggle video proctoring per candidate source */
    @PutMapping("/auth/admin/proctoring-settings")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> updateSettings(
            @RequestHeader(value = "X-User-Id", required = false) String callerId,
            @RequestHeader(value = "X-User-Role", required = false) String callerRole,
            @RequestBody UpdateProctoringSettingsRequest request) {
        if (request == null || request.getSettings() == null || request.getSettings().isEmpty()) {
            throw new IllegalArgumentException("settings map is required");
        }

        Map<String, Boolean> updated = proctoringSettingsService.updateSettings(request.getSettings());
        auditService.record(
                callerId != null ? callerId : "system",
                null,
                callerRole != null ? callerRole : "ADMIN",
                "PROCTORING_SETTINGS_UPDATED",
                "SYSTEM",
                "proctoring-settings",
                "Updated video proctoring toggles: " + updated
        );

        Map<String, Object> body = wrapSettings(updated);
        body.put("ok", true);
        return ResponseEntity.ok(body);
    }

    private static Map<String, Object> wrapSettings(Map<String, Boolean> settings) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("settings", settings);
        return body;
    }
}
