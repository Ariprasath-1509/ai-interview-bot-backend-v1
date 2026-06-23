package com.benchreadiness.ai.controller;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/ai/cache")
public class CacheController {

    private final CacheManager cacheManager;

    public CacheController(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getCacheStats() {
        org.springframework.cache.Cache springCache = cacheManager.getCache("rubrics");
        
        if (springCache == null) {
            return ResponseEntity.ok(Map.of("error", "Cache 'rubrics' not found"));
        }

        CaffeineCache caffeineCache = (CaffeineCache) springCache;
        Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
        CacheStats stats = nativeCache.stats();

        Map<String, Object> response = new HashMap<>();
        response.put("cacheName", "rubrics");
        response.put("hitCount", stats.hitCount());
        response.put("missCount", stats.missCount());
        response.put("hitRate", String.format("%.2f%%", stats.hitRate() * 100));
        response.put("missRate", String.format("%.2f%%", stats.missRate() * 100));
        response.put("loadSuccessCount", stats.loadSuccessCount());
        response.put("loadFailureCount", stats.loadFailureCount());
        response.put("totalLoadTime", stats.totalLoadTime());
        response.put("evictionCount", stats.evictionCount());
        response.put("estimatedSize", nativeCache.estimatedSize());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/entries")
    public ResponseEntity<?> getCacheEntries() {
        org.springframework.cache.Cache springCache = cacheManager.getCache("rubrics");
        
        if (springCache == null) {
            return ResponseEntity.ok(Map.of("error", "Cache 'rubrics' not found"));
        }

        CaffeineCache caffeineCache = (CaffeineCache) springCache;
        Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();

        Map<String, Object> response = new HashMap<>();
        response.put("cacheName", "rubrics");
        response.put("size", nativeCache.estimatedSize());
        response.put("keys", nativeCache.asMap().keySet());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/clear")
    public ResponseEntity<?> clearCache() {
        org.springframework.cache.Cache springCache = cacheManager.getCache("rubrics");
        
        if (springCache == null) {
            return ResponseEntity.ok(Map.of("error", "Cache 'rubrics' not found"));
        }

        springCache.clear();
        return ResponseEntity.ok(Map.of("message", "Cache 'rubrics' cleared successfully"));
    }

    @GetMapping("/check/{key}")
    public ResponseEntity<?> checkCacheKey(@PathVariable String key) {
        org.springframework.cache.Cache springCache = cacheManager.getCache("rubrics");
        
        if (springCache == null) {
            return ResponseEntity.ok(Map.of("error", "Cache 'rubrics' not found"));
        }

        Object value = springCache.get(key);
        
        Map<String, Object> response = new HashMap<>();
        response.put("key", key);
        response.put("cached", value != null);
        response.put("value", value != null ? "Present" : "Not cached");

        return ResponseEntity.ok(response);
    }
}
