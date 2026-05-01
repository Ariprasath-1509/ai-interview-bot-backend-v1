package com.benchreadiness.interview.repository;

import com.benchreadiness.interview.entity.InterviewQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, Long> {
    
    List<InterviewQuestion> findByInterviewId(String interviewId);
    
    @Query("SELECT q FROM InterviewQuestion q WHERE q.interviewId IN " +
           "(SELECT i.id FROM Interview i JOIN Engineer e ON i.engineerId = e.id " +
           "WHERE e.email IN (SELECT c.email FROM Candidate c WHERE c.id = :candidateId))")
    List<InterviewQuestion> findByCandidateId(@Param("candidateId") Long candidateId);
    
    @Query("SELECT DISTINCT tag FROM InterviewQuestion q JOIN q.tags tag WHERE q.interviewId IN " +
           "(SELECT i.id FROM Interview i JOIN Engineer e ON i.engineerId = e.id " +
           "WHERE e.email IN (SELECT c.email FROM Candidate c WHERE c.id = :candidateId))")
    List<String> findDistinctTagsByCandidateId(@Param("candidateId") Long candidateId);
}