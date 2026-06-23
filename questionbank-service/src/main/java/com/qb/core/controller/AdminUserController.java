package com.qb.core.controller;

import com.qb.client.AuthServiceClient;
import com.qb.core.dto.ApiResponse;
import com.qb.core.dto.UserProfileDTO;
import com.qb.core.entity.InterviewSession;
import com.qb.core.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AuthServiceClient authServiceClient;
    private final SessionRepository sessionRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserProfileDTO>>> getUsers() {
        List<UserProfileDTO> users = new ArrayList<>();

        try {
            List<Map<String, Object>> staff = authServiceClient.getStaff();
            for (Map<String, Object> s : staff) {
                users.add(mapToUserProfile(s, false));
            }
        } catch (Exception e) {
        }

        try {
            List<Map<String, Object>> admins = authServiceClient.getAdmins();
            for (Map<String, Object> a : admins) {
                users.add(mapToUserProfile(a, true));
            }
        } catch (Exception e) {
        }

        return ResponseEntity.ok(ApiResponse.ok(users));
    }

    @PutMapping("/{id}/toggle-admin")
    public ResponseEntity<ApiResponse<Void>> toggleAdmin(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(null, "Toggle admin must be done in auth-service"));
    }

    @PutMapping("/sessions/{sessionId}/link-candidate")
    public ResponseEntity<ApiResponse<Void>> linkCandidate(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> body) {

        String candidateId = body.get("candidateId");
        if (candidateId == null || candidateId.isBlank()) {
            return ResponseEntity.ok(ApiResponse.ok(null, "Candidate ID is required"));
        }

        UUID sessionUuid;
        try {
            sessionUuid = UUID.fromString(sessionId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid session ID format"));
        }

        InterviewSession session = sessionRepository.findById(sessionUuid)
                .orElse(null);

        if (session == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Session not found"));
        }

        try {
            UUID candUuid = UUID.fromString(candidateId);
            session.setCandidateId(candUuid);
            sessionRepository.save(session);

            authServiceClient.incrementInterviewCount(candidateId);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to link candidate: " + e.getMessage()));
        }

        return ResponseEntity.ok(ApiResponse.ok(null, "Candidate linked successfully"));
    }

    private UserProfileDTO mapToUserProfile(Map<String, Object> map, boolean isAdmin) {
        Object idObj = map.get("id");
        UUID id = idObj instanceof String ? UUID.fromString((String) idObj) : (UUID) idObj;

        String name = (String) map.getOrDefault("name", "");
        String email = (String) map.getOrDefault("email", "");
        String phone = (String) map.getOrDefault("phone", "");

        Object createdAtObj = map.get("createdAt");
        Instant createdAt = createdAtObj != null ? parseInstant(createdAtObj) : Instant.now();

        long sessionCount = 0;

        return new UserProfileDTO(id, name, email, phone, isAdmin, sessionCount, createdAt);
    }

    private Instant parseInstant(Object obj) {
        if (obj instanceof Number) {
            return Instant.ofEpochMilli(((Number) obj).longValue());
        }
        if (obj instanceof String) {
            return Instant.parse((String) obj);
        }
        return Instant.now();
    }
}