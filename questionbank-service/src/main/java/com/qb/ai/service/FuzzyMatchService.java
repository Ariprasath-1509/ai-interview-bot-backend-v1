package com.qb.ai.service;

import com.qb.ai.dto.DigestParseResponse.ExistingMatch;
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
 * Uses pg_trgm (Postgres trigram extension) to find similar existing questions.
 * Zero API calls — pure database-level fuzzy matching.
 *
 * Returns the best match above SIMILARITY_THRESHOLD along with the actual
 * similarity score (0.0 to 1.0) so the admin can judge relevance on the review screen.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuzzyMatchService {

    private static final double SIMILARITY_THRESHOLD = 0.4;
    private static final int    MAX_MATCHES          = 1;

    private final QuestionRepository  questionRepo;
    private final OccurrenceRepository occurrenceRepo;

    /**
     * Find the best existing match for a given question text.
     * Returns empty if no match above the similarity threshold.
     */
    @Transactional(readOnly = true)
    public Optional<ExistingMatch> findBestMatch(String questionText) {
        try {
            List<Object[]> matches = questionRepo.findSimilarQuestions(
                    questionText, SIMILARITY_THRESHOLD, MAX_MATCHES
            );

            if (matches.isEmpty()) {
                return Optional.empty();
            }

            Object[] row        = matches.getFirst();
            UUID     id         = UUID.fromString(row[0].toString());
            String   text       = (String) row[1];
            double   similarity = ((Number) row[2]).doubleValue();

            List<String> companies = occurrenceRepo.findCompanyNamesByQuestionId(id);

            log.debug("Fuzzy match found: '{}' ~ '{}' (score={})", questionText, text, similarity);

            return Optional.of(new ExistingMatch(id, text, similarity, companies));

        } catch (Exception e) {
            log.warn("Fuzzy match failed for text '{}': {}", questionText, e.getMessage());
            return Optional.empty();
        }
    }
}
