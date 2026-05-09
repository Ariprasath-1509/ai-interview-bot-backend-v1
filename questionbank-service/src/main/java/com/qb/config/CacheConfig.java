package com.qb.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Value("${app.cache.questions-ttl:600}")
    private long questionsTtl;

    @Value("${app.cache.question-ttl:1800}")
    private long questionTtl;

    @Value("${app.cache.companies-ttl:3600}")
    private long companiesTtl;

    @Value("${app.cache.tags-ttl:3600}")
    private long tagsTtl;

    @Value("${app.cache.sessions-ttl:1800}")
    private long sessionsTtl;

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                build("questions",       questionsTtl,  500),
                build("question",        questionTtl,   500),
                build("companies",       companiesTtl,  50),
                build("tags",            tagsTtl,        50),
                build("sessions",        sessionsTtl,   100),
                build("categories",      3600,           50),
                build("admin-dashboard", 3600,           10)
        ));
        return manager;
    }

    private CaffeineCache build(String name, long ttlSeconds, int maxSize) {
        return new CaffeineCache(name,
                Caffeine.newBuilder()
                        .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
                        .maximumSize(maxSize)
                        .recordStats()
                        .build());
    }
}
