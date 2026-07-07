package com.benchreadiness.screening.repository;

import com.benchreadiness.screening.entity.ScreeningCandidate;
import com.benchreadiness.screening.entity.enums.CandidateStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScreeningCandidateRepository extends JpaRepository<ScreeningCandidate, String> {
    Optional<ScreeningCandidate> findByToken(String token);
    List<ScreeningCandidate> findByBatchId(String batchId);
    List<ScreeningCandidate> findByBatchIdAndStage(String batchId, CandidateStage stage);
    List<ScreeningCandidate> findByStage(CandidateStage stage);
    long countByBatchId(String batchId);
    long countByBatchIdAndStageIn(String batchId, List<CandidateStage> stages);
}
