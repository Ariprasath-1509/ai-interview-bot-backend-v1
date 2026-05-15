package com.qb.core.repository;

import com.qb.core.entity.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<InterviewSession, UUID> {

    List<InterviewSession> findByCompanyId(UUID companyId);

    @Query("SELECT s FROM InterviewSession s JOIN FETCH s.company ORDER BY s.interviewDate DESC")
    List<InterviewSession> findAllWithCompany();

    @Query("SELECT s FROM InterviewSession s JOIN FETCH s.company WHERE s.candidateId = :candidateId ORDER BY s.interviewDate DESC")
    List<InterviewSession> findByCandidateId(@Param("candidateId") java.util.UUID candidateId);

    @Query("SELECT COUNT(DISTINCT s.company.id) FROM InterviewSession s WHERE s.candidateId = :candidateId")
    long countDistinctCompaniesByCandidateId(@Param("candidateId") java.util.UUID candidateId);

    @Query("SELECT MAX(s.interviewDate) FROM InterviewSession s WHERE s.candidateId = :candidateId")
    java.util.Optional<java.time.LocalDate> findLatestInterviewDateByCandidateId(@Param("candidateId") java.util.UUID candidateId);

    @Query("SELECT COUNT(DISTINCT s.candidateId) FROM InterviewSession s WHERE s.candidateId IS NOT NULL")
    long countDistinctCandidates();

    @Query("SELECT MAX(s.createdAt) FROM InterviewSession s")
    java.util.Optional<java.time.Instant> findMostRecentCreatedAt();
}
