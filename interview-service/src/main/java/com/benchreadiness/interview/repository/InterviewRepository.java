package com.benchreadiness.interview.repository;

import com.benchreadiness.interview.entity.Interview;
import com.benchreadiness.interview.entity.InterviewStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InterviewRepository extends JpaRepository<Interview, String> {
    List<Interview> findByEngineerId(String engineerId);
    List<Interview> findByStatus(InterviewStatus status);
    List<Interview> findByStatusIn(List<InterviewStatus> statuses);
    
    @Query("SELECT i FROM Interview i JOIN Engineer e ON i.engineerId = e.id WHERE e.email = :email ORDER BY i.createdAt DESC")
    List<Interview> findByCandidateEmailOrderByCreatedAtDesc(@Param("email") String email);

    @Query("SELECT i FROM Interview i WHERE i.createdAt >= :startOfDay AND i.createdAt < :endOfDay")
    List<Interview> findCreatedToday(@Param("startOfDay") Instant startOfDay, @Param("endOfDay") Instant endOfDay);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Interview i WHERE i.id = :id")
    Optional<Interview> findByIdForUpdate(@Param("id") String id);

    @Query("SELECT i FROM Interview i ORDER BY i.createdAt DESC")
    List<Interview> findAllPaged(Pageable pageable);
}