package com.benchreadiness.interview.repository;

import com.benchreadiness.interview.entity.Interview;
import com.benchreadiness.interview.entity.InterviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface InterviewRepository extends JpaRepository<Interview, String> {
    List<Interview> findByEngineerId(String engineerId);
    List<Interview> findByStatus(InterviewStatus status);
    List<Interview> findByStatusIn(List<InterviewStatus> statuses);
    
    @Query("SELECT i FROM Interview i JOIN Engineer e ON i.engineerId = e.id WHERE e.email = :email ORDER BY i.createdAt DESC")
    List<Interview> findByCandidateEmailOrderByCreatedAtDesc(@Param("email") String email);

    @Query("SELECT i FROM Interview i WHERE i.createdAt >= :startOfDay AND i.createdAt < :endOfDay")
    List<Interview> findCreatedToday(@Param("startOfDay") Instant startOfDay, @Param("endOfDay") Instant endOfDay);
}