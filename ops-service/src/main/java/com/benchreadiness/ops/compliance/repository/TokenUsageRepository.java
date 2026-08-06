package com.benchreadiness.ops.compliance.repository;

import com.benchreadiness.ops.compliance.entity.TokenUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface TokenUsageRepository extends JpaRepository<TokenUsage, String> {

    @Query("SELECT SUM(t.totalTokens) FROM TokenUsage t WHERE t.createdAt >= :startOfDay AND t.createdAt < :startOfNextDay")
    Integer getTotalTokensForDate(@Param("startOfDay") Instant startOfDay, @Param("startOfNextDay") Instant startOfNextDay);

    @Query("SELECT SUM(t.totalTokens) FROM TokenUsage t WHERE t.createdByUserId = :userId AND t.createdAt >= :startOfDay AND t.createdAt < :startOfNextDay")
    Integer getTotalTokensForUserAndDate(@Param("userId") String userId, @Param("startOfDay") Instant startOfDay, @Param("startOfNextDay") Instant startOfNextDay);

    @Query("SELECT t FROM TokenUsage t WHERE t.interviewId = :interviewId ORDER BY t.createdAt")
    List<TokenUsage> findByInterviewId(@Param("interviewId") String interviewId);

    @Query("SELECT t FROM TokenUsage t WHERE t.createdAt >= :startOfDay AND t.createdAt < :startOfNextDay ORDER BY t.createdAt DESC")
    List<TokenUsage> findByDate(@Param("startOfDay") Instant startOfDay, @Param("startOfNextDay") Instant startOfNextDay);

    @Query("SELECT SUM(t.totalTokens) FROM TokenUsage t WHERE t.createdAt >= :start AND t.createdAt < :end")
    Integer getTotalTokensInRange(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT SUM(t.estimatedCostUsd) FROM TokenUsage t WHERE t.createdAt >= :start AND t.createdAt < :end")
    java.math.BigDecimal getTotalCostInRange(@Param("start") Instant start, @Param("end") Instant end);
}
