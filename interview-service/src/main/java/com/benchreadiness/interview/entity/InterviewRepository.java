package com.benchreadiness.interview.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface InterviewRepository extends JpaRepository<Interview, String> {
    List<Interview> findByEngineerId(String engineerId);
    List<Interview> findByStatus(InterviewStatus status);

    @Query("SELECT i FROM Interview i WHERE i.createdAt >= :startOfDay AND i.createdAt < :endOfDay")
    List<Interview> findCreatedToday(@Param("startOfDay") Instant startOfDay, @Param("endOfDay") Instant endOfDay);
}
