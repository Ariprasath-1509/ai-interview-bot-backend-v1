package com.qb.core.controller;

import com.qb.core.dto.ApiResponse;
import com.qb.core.dto.SessionDTO;
import com.qb.core.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SessionDTO>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(sessionService.getAllSessions()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        sessionService.deleteSession(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Session deleted"));
    }
}
