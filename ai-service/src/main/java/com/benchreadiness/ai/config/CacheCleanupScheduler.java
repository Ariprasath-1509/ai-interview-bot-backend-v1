package com.benchreadiness.ai.config;

import com.benchreadiness.ai.service.QuestionCacheService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CacheCleanupScheduler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CacheCleanupScheduler.class);
    
    private final QuestionCacheService cacheService;

    public CacheCleanupScheduler(QuestionCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Scheduled(fixedRate = 3600000) // Every hour
    public void cleanupExpiredCacheEntries() {
        log.debug("Running cache cleanup...");
        cacheService.clearExpiredEntries();
    }
}