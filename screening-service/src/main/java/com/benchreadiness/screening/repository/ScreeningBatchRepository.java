package com.benchreadiness.screening.repository;

import com.benchreadiness.screening.entity.ScreeningBatch;
import com.benchreadiness.screening.entity.enums.BatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ScreeningBatchRepository extends JpaRepository<ScreeningBatch, String> {
    List<ScreeningBatch> findByAssignerUserIdOrderByCreatedAtDesc(String assignerUserId);
    List<ScreeningBatch> findByStatusAndDeadlineBefore(BatchStatus status, Instant instant);
    List<ScreeningBatch> findByStatus(BatchStatus status);
}
