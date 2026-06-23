package com.qb.core.service;

import com.qb.core.dto.QuestionDTO;
import com.qb.core.entity.Question;
import com.qb.core.repository.CompanyRepository;
import com.qb.core.repository.OccurrenceRepository;
import com.qb.core.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Relevancy Score Engine
 *
 * Computes a score (0-100) per question from three signals:
 *   Frequency 40% + Recency 40% + Spread 20%
 *
 * Scores are stored in questions.relevancy_score + questions.relevancy_label.
 * Recomputed: (1) after every digest commit, (2) nightly at 5:30 AM IST.
 *
 * See RelevancyScore.md for full documentation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelevancyScoreService {

    private static final double WEIGHT_FREQUENCY = 0.40;
    private static final double WEIGHT_RECENCY   = 0.40;
    private static final double WEIGHT_SPREAD    = 0.20;

    private final QuestionRepository   questionRepo;
    private final OccurrenceRepository occurrenceRepo;
    private final CompanyRepository    companyRepo;

    /**
     * Recompute and persist relevancy scores for ALL questions.
     * Called after digest commit and by the nightly scheduler.
     */
    @Transactional
    public void recomputeAll() {
        List<Question> questions    = questionRepo.findAll();
        int  maxOccurrence          = questions.stream()
                .mapToInt(q -> q.getOccurrenceCount() != null ? q.getOccurrenceCount() : 0)
                .max().orElse(1);
        long totalCompanies         = companyRepo.count();

        log.info("Recomputing relevancy for {} questions (maxOcc={}, companies={})",
                questions.size(), maxOccurrence, totalCompanies);

        for (Question q : questions) {
            List<String>   companies   = occurrenceRepo.findCompanyNamesByQuestionId(q.getId());
            List<Object[]> sessionRows = occurrenceRepo.findSessionInfoByQuestionId(q.getId(), null, null);

            List<LocalDate> dates = sessionRows.stream()
                    .map(row -> row[2] != null ? ((java.sql.Date) row[2]).toLocalDate() : null)
                    .filter(d -> d != null)
                    .toList();

            double score = computeScore(
                    q.getOccurrenceCount() != null ? q.getOccurrenceCount() : 0,
                    maxOccurrence, dates, companies.size(), totalCompanies
            );
            questionRepo.updateRelevancy(q.getId(), score, toLabel(score));
        }

        log.info("Relevancy recompute complete.");
    }

    /**
     * Compute score from a QuestionDTO (used in getById read path).
     */
    public double computeScore(QuestionDTO dto, int maxOccurrence, long totalCompanies) {
        List<LocalDate> dates = dto.sessions().stream()
                .map(QuestionDTO.SessionInfo::interviewDate)
                .filter(d -> d != null)
                .toList();
        return computeScore(dto.occurrenceCount(), maxOccurrence, dates,
                dto.askedByCompanies().size(), totalCompanies);
    }

    public String toLabel(double score) {
        if (score >= 75) return "CRITICAL";
        if (score >= 50) return "HIGH";
        if (score >= 25) return "MODERATE";
        return "LOW";
    }

    // -- Private --

    private double computeScore(int occurrenceCount, int maxOccurrence,
                                List<LocalDate> dates, int distinctCompanies, long totalCompanies) {
        return (computeFrequencyScore(occurrenceCount, maxOccurrence) * WEIGHT_FREQUENCY)
             + (computeRecencyScore(dates)                            * WEIGHT_RECENCY)
             + (computeSpreadScore(distinctCompanies, totalCompanies) * WEIGHT_SPREAD);
    }

    private double computeFrequencyScore(int occurrenceCount, int maxOccurrence) {
        if (maxOccurrence <= 1) return 50.0;
        return ((double) occurrenceCount / maxOccurrence) * 100.0;
    }

    private double computeRecencyScore(List<LocalDate> dates) {
        if (dates == null || dates.isEmpty()) return 10.0;
        LocalDate mostRecent = dates.stream().max(LocalDate::compareTo).orElse(null);
        if (mostRecent == null) return 10.0;
        long daysSince = ChronoUnit.DAYS.between(mostRecent, LocalDate.now());
        if (daysSince <= 30)  return 100.0;
        if (daysSince <= 90)  return 75.0;
        if (daysSince <= 180) return 50.0;
        if (daysSince <= 365) return 25.0;
        return 10.0;
    }

    private double computeSpreadScore(int distinctCompanies, long totalCompanies) {
        if (totalCompanies == 0) return 0.0;
        return ((double) distinctCompanies / totalCompanies) * 100.0;
    }
}
