package com.benchreadiness.interview.feature;

import com.benchreadiness.interview.client.AuthServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feature entitlements live in auth-service (org_features table); this asks over Feign and caches
 * the result briefly per org so a gated endpoint doesn't hit auth-service on every request. Fails
 * open (treats the feature as enabled) on any lookup error — a flaky auth-service call should never
 * take down interview-service for every org.
 */
@Service
public class OrgFeatureGateService {

    private static final Logger logger = LoggerFactory.getLogger(OrgFeatureGateService.class);
    private static final long CACHE_TTL_MILLIS = 30_000;

    private final AuthServiceClient authServiceClient;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public OrgFeatureGateService(AuthServiceClient authServiceClient) {
        this.authServiceClient = authServiceClient;
    }

    public boolean isEnabled(String orgCode, FeatureKey key) {
        if (orgCode == null || orgCode.isBlank()) {
            return true;
        }
        Map<String, Boolean> features = getFeatures(orgCode);
        return features.getOrDefault(key.name(), true);
    }

    private Map<String, Boolean> getFeatures(String orgCode) {
        CacheEntry entry = cache.get(orgCode);
        long now = System.currentTimeMillis();
        if (entry != null && entry.expiresAt > now) {
            return entry.features;
        }
        try {
            Map<String, Object> response = authServiceClient.getMyOrgFeatures();
            @SuppressWarnings("unchecked")
            Map<String, Boolean> features = (Map<String, Boolean>) response.getOrDefault("features", Map.of());
            cache.put(orgCode, new CacheEntry(features, now + CACHE_TTL_MILLIS));
            return features;
        } catch (Exception e) {
            logger.warn("Failed to fetch org features for {} — failing open: {}", orgCode, e.getMessage());
            return Map.of();
        }
    }

    private record CacheEntry(Map<String, Boolean> features, long expiresAt) {}
}
