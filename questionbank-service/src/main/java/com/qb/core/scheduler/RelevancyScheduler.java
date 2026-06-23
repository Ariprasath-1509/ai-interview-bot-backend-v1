package com.qb.core.scheduler;

import com.qb.core.service.RelevancyScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly job that refreshes relevancy scores for all questions.
 * Runs at 5:30 AM IST = 00:00 UTC (IST is UTC+5:30).
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class RelevancyScheduler {

    private final RelevancyScoreService relevancyScoreService;

    @Scheduled(cron = "0 0 0 * * *", zone = "UTC")
    @CacheEvict(value = "questions", allEntries = true)
    public void refreshRelevancyScores() {
        log.info("Nightly relevancy refresh started (5:30 AM IST)");
        relevancyScoreService.recomputeAll();
        log.info("Nightly relevancy refresh complete");
    }
}
