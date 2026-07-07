package com.benchreadiness.screening.repository;

import com.benchreadiness.screening.entity.ScreeningQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScreeningQuestionRepository extends JpaRepository<ScreeningQuestion, String> {
    List<ScreeningQuestion> findByBatchIdOrderByDisplayIndexAsc(String batchId);
}
