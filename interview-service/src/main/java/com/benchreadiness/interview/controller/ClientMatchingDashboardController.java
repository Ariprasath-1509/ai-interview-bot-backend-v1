package com.benchreadiness.interview.controller;

import com.benchreadiness.interview.dto.ClientMatchingOverview;
import com.benchreadiness.interview.dto.ClientMatchingResult;
import com.benchreadiness.interview.service.ClientMatchingDashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/clients/matching")
public class ClientMatchingDashboardController {

    private final ClientMatchingDashboardService dashboardService;

    public ClientMatchingDashboardController(ClientMatchingDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * Get all clients with matching summary
     * GET /clients/matching/overview
     */
    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<Map<String, Object>> getMatchingOverview(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        
        List<ClientMatchingOverview> clients = dashboardService.getAllClientsWithMatchingSummary(userId, userRole);
        
        return ResponseEntity.ok(Map.of(
            "clients", clients,
            "totalClients", clients.size()
        ));
    }

    /**
     * Get detailed matches for a specific client
     * GET /clients/matching/{clientId}?source=BENCH_B2B
     */
    @GetMapping("/{clientId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<ClientMatchingResult> getClientMatches(
            @PathVariable String clientId,
            @RequestParam(defaultValue = "BENCH_B2B") String source,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        
        ClientMatchingResult result = dashboardService.getClientMatches(clientId, source, userId, userRole);
        result.setCacheSource("cached"); // Mark as cached since it came from cache or fresh
        
        return ResponseEntity.ok(result);
    }

    /**
     * Refresh matches for a specific client (bypass cache)
     * POST /clients/matching/{clientId}/refresh
     */
    @PostMapping("/{clientId}/refresh")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ClientMatchingResult> refreshClientMatches(
            @PathVariable String clientId,
            @RequestBody Map<String, String> request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        
        String source = request.getOrDefault("source", "BENCH_B2B");
        
        ClientMatchingResult result = dashboardService.refreshClientMatches(clientId, source, userId, userRole);
        result.setCacheSource("ai-fresh");
        
        return ResponseEntity.ok(result);
    }

    /**
     * Clear all matching caches
     * POST /clients/matching/cache/clear
     */
    @PostMapping("/cache/clear")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> clearCache() {
        dashboardService.clearAllCaches();
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "All client matching caches cleared successfully"
        ));
    }

    /**
     * Get cache statistics
     * GET /clients/matching/cache/stats
     */
    @GetMapping("/cache/stats")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        // This would require accessing CacheManager to get stats
        // For now, return a simple response
        return ResponseEntity.ok(Map.of(
            "cacheEnabled", true,
            "cacheType", "Caffeine (In-Memory)",
            "maxSize", 1000,
            "ttl", "6 hours"
        ));
    }
}
