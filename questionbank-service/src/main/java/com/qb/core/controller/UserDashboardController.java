package com.qb.core.controller;

import com.qb.core.dto.ApiResponse;
import com.qb.core.dto.UserDashboardStatsDTO;
import com.qb.core.entity.InterviewSession;
import com.qb.core.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/user/dashboard")
@RequiredArgsConstructor
public class UserDashboardController {

    private final SessionRepository sessionRepository;

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<UserDashboardStatsDTO>> getDashboardStats(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        if (userId == null || userId.isBlank()) {
            return ResponseEntity.ok(ApiResponse.ok(new UserDashboardStatsDTO(0, 0, null)));
        }

        try {
            UUID candidateId = UUID.fromString(userId);

            List<InterviewSession> sessions = sessionRepository.findByCandidateId(candidateId);
            long totalSessions = sessions.size();

            long totalCompanies = sessionRepository.countDistinctCompaniesByCandidateId(candidateId);

            LocalDate lastInterviewDate = sessionRepository.findLatestInterviewDateByCandidateId(candidateId)
                    .orElse(null);

            return ResponseEntity.ok(ApiResponse.ok(
                    new UserDashboardStatsDTO(totalSessions, totalCompanies, lastInterviewDate)
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.ok(new UserDashboardStatsDTO(0, 0, null)));
        }
    }
}