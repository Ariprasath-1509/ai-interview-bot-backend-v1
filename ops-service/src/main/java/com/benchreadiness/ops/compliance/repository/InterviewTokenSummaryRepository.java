package com.benchreadiness.ops.compliance.repository;

import com.benchreadiness.ops.compliance.entity.InterviewTokenSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InterviewTokenSummaryRepository extends JpaRepository<InterviewTokenSummary, String> {
    Optional<InterviewTokenSummary> findByInterviewId(String interviewId);
}
