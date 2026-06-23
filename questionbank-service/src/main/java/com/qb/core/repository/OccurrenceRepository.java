package com.qb.core.repository;

import com.qb.core.entity.QuestionOccurrence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;

public interface OccurrenceRepository extends JpaRepository<QuestionOccurrence, UUID> {

    List<QuestionOccurrence> findByQuestionId(UUID questionId);

    List<QuestionOccurrence> findBySessionId(UUID sessionId);

    /**
     * Find all companies where a specific question was asked.
     */
    @Query("""
            SELECT DISTINCT c.name FROM QuestionOccurrence qo
            JOIN qo.session s
            JOIN s.company c
            WHERE qo.question.id = :questionId
            """)
    List<String> findCompanyNamesByQuestionId(@Param("questionId") UUID questionId);

    /**
     * Batch: find company names for multiple questions in a single query.
     * Returns [questionId, companyName] rows.
     * Eliminates N+1 when loading a page of questions.
     */
    @Query(value = """
            SELECT DISTINCT qo.question_id, c.name
            FROM questionbank_svc.question_occurrences qo
            JOIN questionbank_svc.interview_sessions s ON s.id = qo.session_id
            JOIN questionbank_svc.companies c ON c.id = s.company_id
            WHERE qo.question_id IN (:questionIds)
            ORDER BY qo.question_id, c.name
            """, nativeQuery = true)
    List<Object[]> findCompanyNamesByQuestionIds(@Param("questionIds") List<UUID> questionIds);

    /**
     * Find session info (company, round, date) for a question — filtered by optional companySlug and round.
     */
    @Query(value = """
            SELECT DISTINCT c.name, s.round, s.interview_date
            FROM questionbank_svc.question_occurrences qo
            JOIN questionbank_svc.interview_sessions s ON s.id = qo.session_id
            JOIN questionbank_svc.companies c ON c.id = s.company_id
            WHERE qo.question_id = :questionId
              AND (CAST(:companySlug AS TEXT) IS NULL OR c.slug = CAST(:companySlug AS TEXT))
              AND (CAST(:round AS TEXT) IS NULL OR LOWER(s.round) = LOWER(CAST(:round AS TEXT)))
            ORDER BY s.interview_date DESC
            """, nativeQuery = true)
    List<Object[]> findSessionInfoByQuestionId(
            @Param("questionId") UUID questionId,
            @Param("companySlug") String companySlug,
            @Param("round") String round
    );

    /**
     * Batch: find session info for multiple questions in a single query.
     * Returns [questionId, companyName, round, interviewDate] rows.
     * Eliminates N+1 when loading a page of questions.
     */
    @Query(value = """
            SELECT DISTINCT qo.question_id, c.name, s.round, s.interview_date
            FROM questionbank_svc.question_occurrences qo
            JOIN questionbank_svc.interview_sessions s ON s.id = qo.session_id
            JOIN questionbank_svc.companies c ON c.id = s.company_id
            WHERE qo.question_id IN (:questionIds)
              AND (CAST(:companySlug AS TEXT) IS NULL OR c.slug = CAST(:companySlug AS TEXT))
              AND (CAST(:round AS TEXT) IS NULL OR LOWER(s.round) = LOWER(CAST(:round AS TEXT)))
            ORDER BY qo.question_id, s.interview_date DESC
            """, nativeQuery = true)
    List<Object[]> findSessionInfoByQuestionIds(
            @Param("questionIds") List<UUID> questionIds,
            @Param("companySlug") String companySlug,
            @Param("round") String round
    );
}
