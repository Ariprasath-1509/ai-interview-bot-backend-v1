package com.qb.core.repository;

import com.qb.core.entity.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    /**
     * Full search & filter with pagination.
     * All params are optional — null means "no filter".
     */
    @Query(value = """
            SELECT DISTINCT q.id, q.text, q.category_id, q.occurrence_count, q.created_at, q.updated_at,
                           q.relevancy_score, q.relevancy_label
            FROM questionbank_svc.questions q
            LEFT JOIN questionbank_svc.categories cat ON cat.id = q.category_id
            LEFT JOIN questionbank_svc.question_tags qt ON qt.question_id = q.id
            LEFT JOIN questionbank_svc.tags t ON t.id = qt.tag_id
            LEFT JOIN questionbank_svc.question_occurrences qo ON qo.question_id = q.id
            LEFT JOIN questionbank_svc.interview_sessions s ON s.id = qo.session_id
            LEFT JOIN questionbank_svc.companies c ON c.id = s.company_id
            WHERE (CAST(:search AS TEXT) IS NULL OR LOWER(q.text) LIKE LOWER(CONCAT('%', CAST(:search AS TEXT), '%')))
              AND (CAST(:interviewType AS TEXT) IS NULL OR cat.interview_type = CAST(:interviewType AS TEXT))
              AND (CAST(:category AS TEXT) IS NULL OR LOWER(cat.name) = LOWER(CAST(:category AS TEXT)))
              AND (CAST(:companySlug AS TEXT) IS NULL OR c.slug = CAST(:companySlug AS TEXT))
              AND (CAST(:round AS TEXT) IS NULL OR LOWER(s.round) = LOWER(CAST(:round AS TEXT)))
              AND (CAST(:tagName AS TEXT) IS NULL OR LOWER(t.name) = LOWER(CAST(:tagName AS TEXT)))
              AND (CAST(:importanceLabel AS TEXT) IS NULL OR q.relevancy_label = CAST(:importanceLabel AS TEXT))
            ORDER BY q.relevancy_score DESC
            """,
            countQuery = """
            SELECT COUNT(DISTINCT q.id) FROM questionbank_svc.questions q
            LEFT JOIN questionbank_svc.categories cat ON cat.id = q.category_id
            LEFT JOIN questionbank_svc.question_tags qt ON qt.question_id = q.id
            LEFT JOIN questionbank_svc.tags t ON t.id = qt.tag_id
            LEFT JOIN questionbank_svc.question_occurrences qo ON qo.question_id = q.id
            LEFT JOIN questionbank_svc.interview_sessions s ON s.id = qo.session_id
            LEFT JOIN questionbank_svc.companies c ON c.id = s.company_id
            WHERE (CAST(:search AS TEXT) IS NULL OR LOWER(q.text) LIKE LOWER(CONCAT('%', CAST(:search AS TEXT), '%')))
              AND (CAST(:interviewType AS TEXT) IS NULL OR cat.interview_type = CAST(:interviewType AS TEXT))
              AND (CAST(:category AS TEXT) IS NULL OR LOWER(cat.name) = LOWER(CAST(:category AS TEXT)))
              AND (CAST(:companySlug AS TEXT) IS NULL OR c.slug = CAST(:companySlug AS TEXT))
              AND (CAST(:round AS TEXT) IS NULL OR LOWER(s.round) = LOWER(CAST(:round AS TEXT)))
              AND (CAST(:tagName AS TEXT) IS NULL OR LOWER(t.name) = LOWER(CAST(:tagName AS TEXT)))
              AND (CAST(:importanceLabel AS TEXT) IS NULL OR q.relevancy_label = CAST(:importanceLabel AS TEXT))
            """,
            nativeQuery = true)
    Page<Question> searchQuestions(
            @Param("search") String search,
            @Param("interviewType") String interviewType,
            @Param("category") String category,
            @Param("companySlug") String companySlug,
            @Param("round") String round,
            @Param("tagName") String tagName,
            @Param("importanceLabel") String importanceLabel,
            Pageable pageable
    );

    /**
     * Fuzzy match using pg_trgm — finds questions similar to the input text.
     * Returns Object[] with [id, text, score] for each match.
     */
    @Query(value = """
            SELECT q.id, q.text, similarity(q.text, :text) AS score
            FROM questionbank_svc.questions q
            WHERE similarity(q.text, :text) > :threshold
            ORDER BY score DESC
            LIMIT :maxResults
            """, nativeQuery = true)
    List<Object[]> findSimilarQuestions(
            @Param("text") String text,
            @Param("threshold") double threshold,
            @Param("maxResults") int maxResults
    );

    /**
     * Get all questions asked at a specific company.
     */
    @Query("""
            SELECT DISTINCT q FROM Question q
            JOIN QuestionOccurrence qo ON qo.question = q
            JOIN qo.session s
            JOIN s.company c
            WHERE c.slug = :companySlug
            ORDER BY q.occurrenceCount DESC
            """)
    List<Question> findByCompanySlug(@Param("companySlug") String companySlug);

    /**
     * Bulk update relevancy score and label for a question.
     */
    @Modifying
    @Query("UPDATE Question q SET q.relevancyScore = :score, q.relevancyLabel = :label WHERE q.id = :id")
    void updateRelevancy(@Param("id") java.util.UUID id,
                         @Param("score") double score,
                         @Param("label") String label);

    /**
     * Get the maximum occurrence_count across all questions.
     * Used by RelevancyScoreService for frequency score normalization.
     */
    @Query("SELECT COALESCE(MAX(q.occurrenceCount), 1) FROM Question q")
    int findMaxOccurrenceCount();

    /**
     * Count questions by category.
     */
    long countByCategoryId(UUID categoryId);

    /**
     * Find all questions in a category.
     */
    List<Question> findByCategoryId(UUID categoryId);

    /**
     * Count questions by relevancy label.
     */
    long countByRelevancyLabel(String label);
}
