package com.benchreadiness.interview.repository;

import com.benchreadiness.interview.entity.InterviewFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewFeedbackRepository extends JpaRepository<InterviewFeedback, Long> {
    
    List<InterviewFeedback> findByInterviewId(String interviewId);
    
    @Query("SELECT AVG(f.rating) FROM InterviewFeedback f WHERE f.interviewId IN " +
           "(SELECT i.id FROM Interview i JOIN Engineer e ON i.engineerId = e.id " +
           "WHERE e.email IN (SELECT c.email FROM Candidate c WHERE c.id = :candidateId))")
    Optional<Double> findAverageRatingByCandidateId(@Param("candidateId") Long candidateId);
    
    @Query("SELECT f FROM InterviewFeedback f WHERE f.interviewId IN " +
           "(SELECT i.id FROM Interview i JOIN Engineer e ON i.engineerId = e.id " +
           "WHERE e.email IN (SELECT c.email FROM Candidate c WHERE c.id = :candidateId)) " +
           "ORDER BY f.createdAt DESC")
    List<InterviewFeedback> findByCandidateIdOrderByCreatedAtDesc(@Param("candidateId") Long candidateId);
}