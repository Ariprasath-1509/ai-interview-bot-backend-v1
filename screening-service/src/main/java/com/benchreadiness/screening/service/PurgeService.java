package com.benchreadiness.screening.service;

import com.benchreadiness.screening.entity.ScreeningBatch;
import com.benchreadiness.screening.entity.enums.BatchStatus;
import com.benchreadiness.screening.repository.ScreeningAnswerRepository;
import com.benchreadiness.screening.repository.ScreeningBatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Deletes raw Round 1 question/answer text once the assigner's report has been sent and the
 * retention window has passed. The candidate's name/email/score/stage row itself is kept — it's
 * needed to keep tracking them through Round 2/3 — only the raw test content is purged.
 */
@Service
public class PurgeService {

    private static final Logger log = LoggerFactory.getLogger(PurgeService.class);

    private final ScreeningBatchRepository batchRepository;
    private final ScreeningAnswerRepository answerRepository;

    @Value("${app.screening.retention-hours:72}")
    private int retentionHours;

    public PurgeService(ScreeningBatchRepository batchRepository, ScreeningAnswerRepository answerRepository) {
        this.batchRepository = batchRepository;
        this.answerRepository = answerRepository;
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 120_000)
    @Transactional
    public void purgeExpiredBatches() {
        Instant cutoff = Instant.now().minus(retentionHours, ChronoUnit.HOURS);
        for (ScreeningBatch batch : batchRepository.findByStatus(BatchStatus.REPORTED)) {
            if (batch.getReportSentAt() != null && batch.getReportSentAt().isBefore(cutoff)) {
                try {
                    answerRepository.deleteByCandidate_Batch_Id(batch.getId());
                    batch.setStatus(BatchStatus.PURGED);
                    batchRepository.save(batch);
                    log.info("Purged raw Round 1 answer content for batch {}", batch.getId());
                } catch (Exception e) {
                    log.error("Failed to purge batch {}", batch.getId(), e);
                }
            }
        }
    }
}
