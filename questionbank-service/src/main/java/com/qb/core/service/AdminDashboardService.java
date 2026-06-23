package com.qb.core.service;

import com.qb.core.dto.AdminStatsDTO;
import com.qb.core.repository.CompanyRepository;
import com.qb.core.repository.QuestionRepository;
import com.qb.core.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final QuestionRepository questionRepo;
    private final CompanyRepository  companyRepo;
    private final SessionRepository  sessionRepo;

    @Cacheable(value = "admin-dashboard", key = "'stats'")
    @Transactional(readOnly = true)
    public AdminStatsDTO getStats() {
        long totalQuestions  = questionRepo.count();
        long totalCompanies  = companyRepo.count();
        long totalSessions   = sessionRepo.count();
        long totalCandidates = sessionRepo.countDistinctCandidates();

        Map<String, Long> byImportance = Map.of(
                "CRITICAL", questionRepo.countByRelevancyLabel("CRITICAL"),
                "HIGH",     questionRepo.countByRelevancyLabel("HIGH"),
                "MODERATE", questionRepo.countByRelevancyLabel("MODERATE"),
                "LOW",      questionRepo.countByRelevancyLabel("LOW")
        );

        Instant lastDigest = sessionRepo.findMostRecentCreatedAt().orElse(null);

        return new AdminStatsDTO(
                totalQuestions, totalCompanies, totalSessions,
                totalCandidates, byImportance, lastDigest
        );
    }
}
