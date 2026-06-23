package com.benchreadiness.interview.repository;

import com.benchreadiness.interview.entity.InterviewQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, Long> {
    
    List<InterviewQuestion> findByInterviewIdOrderBySlotNumberAsc(String interviewId);

    Optional<InterviewQuestion> findByInterviewIdAndSlotNumber(String interviewId, int slotNumber);
}