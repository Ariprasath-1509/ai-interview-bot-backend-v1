package com.benchreadiness.compliance.repository;

import com.benchreadiness.compliance.entity.AssessmentResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AssessmentResponseRepository extends JpaRepository<AssessmentResponse, String> {
    
    Optional<AssessmentResponse> findByInterviewId(String interviewId);
    
    @Query("SELECT ar FROM AssessmentResponse ar WHERE ar.createdAt >= :startDate")
    List<AssessmentResponse> findByCreatedAtAfter(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT ar FROM AssessmentResponse ar WHERE ar.assessmentSource = :source")
    List<AssessmentResponse> findByAssessmentSource(@Param("source") String source);
    
    @Query("SELECT COUNT(ar) FROM AssessmentResponse ar WHERE ar.createdAt >= :startDate")
    Long countByCreatedAtAfter(@Param("startDate") LocalDateTime startDate);
}