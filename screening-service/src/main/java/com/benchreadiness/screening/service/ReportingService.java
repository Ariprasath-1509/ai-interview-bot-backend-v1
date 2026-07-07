package com.benchreadiness.screening.service;

import com.benchreadiness.screening.entity.ScreeningBatch;
import com.benchreadiness.screening.entity.ScreeningCandidate;
import com.benchreadiness.screening.entity.enums.BatchStatus;
import com.benchreadiness.screening.entity.enums.CandidateStage;
import com.benchreadiness.screening.mail.ScreeningMailService;
import com.benchreadiness.screening.repository.ScreeningBatchRepository;
import com.benchreadiness.screening.repository.ScreeningCandidateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Closes batches once every candidate has submitted (or the deadline passes) and sends the
 * assigner one consolidated report per batch — no per-candidate emails.
 */
@Service
public class ReportingService {

    private static final Logger log = LoggerFactory.getLogger(ReportingService.class);

    private static final Set<CandidateStage> STILL_PENDING = Set.of(
            CandidateStage.ROUND1_PENDING, CandidateStage.ROUND1_IN_PROGRESS);

    private final ScreeningBatchRepository batchRepository;
    private final ScreeningCandidateRepository candidateRepository;
    private final ScreeningMailService mailService;

    public ReportingService(ScreeningBatchRepository batchRepository,
                            ScreeningCandidateRepository candidateRepository,
                            ScreeningMailService mailService) {
        this.batchRepository = batchRepository;
        this.candidateRepository = candidateRepository;
        this.mailService = mailService;
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    @Transactional
    public void closeAndReportBatches() {
        for (ScreeningBatch batch : batchRepository.findByStatus(BatchStatus.OPEN)) {
            long stillPending = candidateRepository.countByBatchIdAndStageIn(batch.getId(), List.copyOf(STILL_PENDING));
            boolean deadlinePassed = Instant.now().isAfter(batch.getDeadline());

            if (stillPending == 0) {
                batch.setStatus(BatchStatus.CLOSED_ALL_SUBMITTED);
                batchRepository.save(batch);
            } else if (deadlinePassed) {
                batch.setStatus(BatchStatus.CLOSED_DEADLINE);
                batchRepository.save(batch);
            }
        }

        for (ScreeningBatch batch : batchRepository.findByStatus(BatchStatus.CLOSED_ALL_SUBMITTED)) {
            sendReport(batch);
        }
        for (ScreeningBatch batch : batchRepository.findByStatus(BatchStatus.CLOSED_DEADLINE)) {
            sendReport(batch);
        }
    }

    private void sendReport(ScreeningBatch batch) {
        try {
            List<ScreeningCandidate> candidates = candidateRepository.findByBatchId(batch.getId());
            mailService.sendConsolidatedReport(batch, candidates);
            batch.setStatus(BatchStatus.REPORTED);
            batch.setReportSentAt(Instant.now());
            batchRepository.save(batch);
        } catch (Exception e) {
            log.error("Failed to send consolidated report for batch {}", batch.getId(), e);
        }
    }
}
