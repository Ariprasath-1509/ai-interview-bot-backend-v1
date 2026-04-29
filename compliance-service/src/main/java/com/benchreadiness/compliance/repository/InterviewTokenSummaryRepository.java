package com.benchreadiness.compliance.repository;

import com.benchreadiness.compliance.entity.InterviewTokenSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewTokenSummaryRepository extends JpaRepository<InterviewTokenSummary, String> {
    
    Optional<InterviewTokenSummary> findByInterviewId(String interviewId);
    
    @Query("SELECT its FROM InterviewTokenSummary its WHERE its.createdAt >= :startDate")
    List<InterviewTokenSummary> findByCreatedAtAfter(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT SUM(its.totalTokens) FROM InterviewTokenSummary its WHERE its.createdAt >= :startDate")
    Long sumTotalTokensByCreatedAtAfter(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT SUM(its.totalCostUsd) FROM InterviewTokenSummary its WHERE its.createdAt >= :startDate")
    BigDecimal sumTotalCostByCreatedAtAfter(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT COUNT(its) FROM InterviewTokenSummary its WHERE its.createdAt >= :startDate")
    Long countByCreatedAtAfter(@Param("startDate") LocalDateTime startDate);
}