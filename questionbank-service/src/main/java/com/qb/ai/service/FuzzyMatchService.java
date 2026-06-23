package com.qb.ai.service;

import com.qb.ai.dto.DigestParseResponse.ExistingMatch;
import com.qb.config.FuzzyMatchProperties;
import com.qb.core.repository.OccurrenceRepository;
import com.qb.core.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Uses pg_trgm to suggest duplicate questions during digest parse.
 * Suggestions are conservative — linking on commit is validated separately.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuzzyMatchService {

    private static final int MAX_MATCHES = 1;

    private final FuzzyMatchProperties properties;
    private final QuestionRepository questionRepo;
    private final OccurrenceRepository occurrenceRepo;

    /**
     * Suggest a possible duplicate for admin review (does not auto-link).
     */
    @Transactional(readOnly = true)
    public Optional<ExistingMatch> findBestMatch(String questionText, String category) {
        String text = normalizeText(questionText);
        if (text == null) {
            return Optional.empty();
        }

        double threshold = thresholdForCategory(category);
        String categoryFilter = categoryFilter(category);

        try {
            List<Object[]> matches = questionRepo.findSimilarQuestions(
                    text, threshold, categoryFilter, MAX_MATCHES
            );

            if (matches.isEmpty()) {
                return Optional.empty();
            }

            Object[] row = matches.getFirst();
            UUID id = UUID.fromString(row[0].toString());
            String matchedText = (String) row[1];
            double similarity = ((Number) row[2]).doubleValue();

            List<String> companies = occurrenceRepo.findCompanyNamesByQuestionId(id);

            log.debug("Fuzzy match suggestion: '{}' ~ '{}' (score={}, category={})",
                    text, matchedText, similarity, category);

            return Optional.of(new ExistingMatch(id, matchedText, similarity, companies));

        } catch (Exception e) {
            log.warn("Fuzzy match failed for text '{}': {}", text, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Server-side guard: only allow linking when texts are genuinely similar.
     */
    @Transactional(readOnly = true)
    public boolean verifyLinkAllowed(UUID existingQuestionId, String newQuestionText, String category) {
        if (existingQuestionId == null) {
            return false;
        }
        String text = normalizeText(newQuestionText);
        if (text == null) {
            return false;
        }

        Double score = questionRepo.computeSimilarityScore(existingQuestionId, text);
        if (score == null) {
            log.warn("Link rejected — question {} not found", existingQuestionId);
            return false;
        }

        double required = Math.max(properties.getCommitLinkThreshold(), thresholdForCategory(category));
        boolean allowed = score >= required;
        if (!allowed) {
            log.warn("Link rejected for question {} — score {} below required {}", existingQuestionId, score, required);
        }
        return allowed;
    }

    private double thresholdForCategory(String category) {
        if (category == null || category.isBlank() || "General".equalsIgnoreCase(category.trim())) {
            return properties.getGeneralCategoryThreshold();
        }
        return properties.getSuggestThreshold();
    }

    /** When category is General/blank, search all categories but with a higher threshold. */
    private String categoryFilter(String category) {
        if (category == null || category.isBlank() || "General".equalsIgnoreCase(category.trim())) {
            return null;
        }
        return category.trim();
    }

    private String normalizeText(String questionText) {
        if (questionText == null) {
            return null;
        }
        String trimmed = questionText.trim().replaceAll("\\s+", " ");
        if (trimmed.length() < properties.getMinTextLength()) {
            return null;
        }
        return trimmed;
    }
}
