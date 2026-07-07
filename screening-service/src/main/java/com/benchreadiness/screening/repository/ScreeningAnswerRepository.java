package com.benchreadiness.screening.repository;

import com.benchreadiness.screening.entity.ScreeningAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ScreeningAnswerRepository extends JpaRepository<ScreeningAnswer, String> {
    List<ScreeningAnswer> findByCandidateId(String candidateId);

    // JOIN FETCH the question — open-in-view is off, so the plain derived-query version left
    // `question` as an uninitialized lazy proxy by the time the controller read it, throwing
    // LazyInitializationException outside the (already-closed) transaction.
    @Query("SELECT a FROM ScreeningAnswer a JOIN FETCH a.question WHERE a.candidate.id = :candidateId ORDER BY a.question.displayIndex ASC")
    List<ScreeningAnswer> findByCandidateIdOrderByQuestionDisplayIndexAsc(@Param("candidateId") String candidateId);

    void deleteByCandidate_Batch_Id(String batchId);
}
