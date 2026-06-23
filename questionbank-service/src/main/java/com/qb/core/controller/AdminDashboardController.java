package com.qb.core.controller;

import com.qb.core.dto.AdminStatsDTO;
import com.qb.core.dto.ApiResponse;
import com.qb.core.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin Dashboard endpoints.
 * Route: /api/admin/dashboard/*
 *
 * Follows the pattern:
 *   /api/admin/dashboard/* — admin-only aggregated stats
 *   /api/user/dashboard/*  — user-specific stats (Phase 2C)
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<AdminStatsDTO>> getStats() {
        return ResponseEntity.ok(ApiResponse.ok(adminDashboardService.getStats()));
    }
}
