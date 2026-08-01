package com.benchreadiness.review.feature;

import com.benchreadiness.review.client.AuthServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Caches org feature entitlements from auth-service briefly; fails open on any lookup error. */
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
        return getFeatures(orgCode).getOrDefault(key.name(), true);
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
