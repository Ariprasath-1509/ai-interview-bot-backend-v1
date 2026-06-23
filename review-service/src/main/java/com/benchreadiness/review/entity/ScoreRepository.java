package com.benchreadiness.review.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ScoreRepository extends JpaRepository<Score, String> {
    List<Score> findByInterviewId(String interviewId);
    void deleteByInterviewId(String interviewId);
}
