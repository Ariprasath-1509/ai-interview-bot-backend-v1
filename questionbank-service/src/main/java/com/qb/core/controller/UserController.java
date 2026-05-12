package com.qb.core.controller;

import com.qb.client.AuthServiceClient;
import com.qb.core.dto.ApiResponse;
import com.qb.core.dto.UserProfileDTO;
import com.qb.core.dto.UserSessionDetailsDTO;
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
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthServiceClient authServiceClient;
    private final SessionRepository sessionRepository;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileDTO>> getCurrentUser(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userId == null || userId.isBlank()) {
            return ResponseEntity.ok(ApiResponse.ok(null));
        }

        try {
            Map<String, Object> userData = authServiceClient.getUserById(userId);
            UserProfileDTO profile = mapToUserProfile(userData);
            return ResponseEntity.ok(ApiResponse.ok(profile));
        } catch (Exception e) {
            boolean isAdmin = "ROLE_SUPER_ADMIN".equals(userRole) || "ROLE_ADMIN".equals(userRole) ||
                    "SUPER_ADMIN".equals(userRole) || "ADMIN".equals(userRole);
            UserProfileDTO fallback = new UserProfileDTO(
                    UUID.fromString(userId),
                    "User",
                    "",
                    "",
                    isAdmin,
                    0,
                    Instant.now()
            );
            return ResponseEntity.ok(ApiResponse.ok(fallback));
        }
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileDTO>> updateCurrentUser(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody Map<String, String> body) {

        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("User not authenticated"));
        }

        return getCurrentUser(userId, null);
    }

    @GetMapping("/me/sessions")
    public ResponseEntity<ApiResponse<List<UserSessionDetailsDTO>>> getMySessions(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        if (userId == null || userId.isBlank()) {
            return ResponseEntity.ok(ApiResponse.ok(new ArrayList<>()));
        }

        try {
            UUID uuid = UUID.fromString(userId);
            List<UserSessionDetailsDTO> sessions = sessionRepository.findByCandidateId(uuid)
                    .stream()
                    .map(s -> new UserSessionDetailsDTO(
                            s.getId(),
                            s.getCompany() != null ? s.getCompany().getName() : "Unknown",
                            s.getCompany() != null ? s.getCompany().getSlug() : "",
                            s.getRound(),
                            s.getInterviewDate(),
                            s.getInterviewerName(),
                            new ArrayList<>()
                    ))
                    .toList();
            return ResponseEntity.ok(ApiResponse.ok(sessions));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.ok(new ArrayList<>()));
        }
    }

    private UserProfileDTO mapToUserProfile(Map<String, Object> map) {
        Object idObj = map.get("id");
        UUID id = idObj instanceof String ? UUID.fromString((String) idObj) : (UUID) idObj;

        String name = (String) map.getOrDefault("name", "");
        String email = (String) map.getOrDefault("email", "");
        String phone = (String) map.getOrDefault("phone", "");

        String role = (String) map.getOrDefault("role", "");
        boolean isAdmin = "SUPER_ADMIN".equals(role) || "ADMIN".equals(role);

        Object createdAtObj = map.get("createdAt");
        Instant createdAt = createdAtObj != null ? parseInstant(createdAtObj) : Instant.now();

        return new UserProfileDTO(id, name, email, phone, isAdmin, 0, createdAt);
    }

    private Instant parseInstant(Object obj) {
        if (obj instanceof Number) {
            return Instant.ofEpochMilli(((Number) obj).longValue());
        }
        if (obj instanceof String) {
            try {
                return Instant.parse((String) obj);
            } catch (Exception e) {
                return Instant.now();
            }
        }
        return Instant.now();
    }
}