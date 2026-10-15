package com.benchreadiness.interview.config;

import com.benchreadiness.interview.service.ClientMatchingDashboardService;
import com.benchreadiness.interview.service.MatchingService;
import com.benchreadiness.interview.service.SkillBasedMatchingService;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.support.SimpleCacheManager;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Report item 56: "Cannot find cache named 'eligibleCandidates'". The CacheManager bean replaces
 * the spring.cache.cache-names list in application.yml, so every cache name used by an
 * annotation must be registered in CacheConfig itself.
 */
class CacheConfigTest {

    private final CacheManager manager = build();

    private static CacheManager build() {
        SimpleCacheManager m = (SimpleCacheManager) new CacheConfig().cacheManager();
        m.afterPropertiesSet();
        return m;
    }

    @Test
    void eligibleCandidatesCacheResolves() {
        assertThat(manager.getCache("eligibleCandidates")).isNotNull();
    }

    @Test
    void everyCacheNameUsedByAnnotationsIsRegistered() {
        Set<String> used = new LinkedHashSet<>();
        for (Class<?> c : new Class<?>[]{MatchingService.class, ClientMatchingDashboardService.class,
                                         SkillBasedMatchingService.class}) {
            for (Method m : c.getDeclaredMethods()) {
                Cacheable cacheable = m.getAnnotation(Cacheable.class);
                if (cacheable != null) used.addAll(Set.of(cacheable.value()));
                CacheEvict evict = m.getAnnotation(CacheEvict.class);
                if (evict != null) used.addAll(Set.of(evict.value()));
            }
        }
        assertThat(used).contains("eligibleCandidates", "skillBasedMatches");
        for (String name : used) {
            assertThat(manager.getCache(name)).as("cache '%s'", name).isNotNull();
        }
    }
}
