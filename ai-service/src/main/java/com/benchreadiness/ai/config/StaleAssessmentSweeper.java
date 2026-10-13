package com.benchreadiness.ai.config;

import com.benchreadiness.ai.service.AsyncAssessmentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Marks assessment runs that this instance tracks and that stay PROCESSING too long as FAILED,
 * so pollers get a final state instead of PROCESSING forever. DB rows orphaned by a restart are not swept here.
 */
@Component
public class StaleAssessmentSweeper {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StaleAssessmentSweeper.class);

    private final AsyncAssessmentService asyncAssessmentService;
    private final Duration maxAge;

    public StaleAssessmentSweeper(AsyncAssessmentService asyncAssessmentService,
                                  @Value("${app.assessment.stale-run-minutes:15}") long staleRunMinutes) {
        this.asyncAssessmentService = asyncAssessmentService;
        this.maxAge = Duration.ofMinutes(staleRunMinutes);
    }

    @Scheduled(fixedDelayString = "${app.assessment.stale-sweep-interval-ms:60000}")
    public void sweepStaleRuns() {
        int failed = asyncAssessmentService.failStaleRuns(maxAge, Instant.now());
        if (failed > 0) {
            log.warn("Marked {} stale assessment run(s) FAILED (older than {} min)", failed, maxAge.toMinutes());
        }
    }
}
