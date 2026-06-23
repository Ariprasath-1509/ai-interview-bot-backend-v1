package com.benchreadiness.ops.compliance.repository;

import com.benchreadiness.ops.compliance.entity.AssessmentResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AssessmentResponseRepository extends JpaRepository<AssessmentResponse, String> {
    Optional<AssessmentResponse> findByInterviewId(String interviewId);
}
