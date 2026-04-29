package com.benchreadiness.ai.service;

import org.springframework.stereotype.Service;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QuestionCacheService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(QuestionCacheService.class);
    private static final long TTL_HOURS = 24;
    
    private final ConcurrentHashMap<String, CachedQuestion> cache = new ConcurrentHashMap<>();

    private record CachedQuestion(String question, Instant createdAt) {}

    public String getCachedFirstQuestion(String jdTitle, String jdText, String focusAreas, String interviewMode) {
        String key = generateCacheKey(jdTitle, jdText, focusAreas, interviewMode);
        CachedQuestion cached = cache.get(key);
        
        if (cached != null && !isExpired(cached.createdAt)) {
            log.debug("Cache hit for first question: {}", key.substring(0, 8));
            return cached.question;
        }
        
        if (cached != null) {
            cache.remove(key); // Remove expired entry
        }
        
        return null;
    }

    public void cacheFirstQuestion(String jdTitle, String jdText, String focusAreas, String interviewMode, String question) {
        String key = generateCacheKey(jdTitle, jdText, focusAreas, interviewMode);
        cache.put(key, new CachedQuestion(question, Instant.now()));
        log.debug("Cached first question: {}", key.substring(0, 8));
    }

    private String generateCacheKey(String jdTitle, String jdText, String focusAreas, String interviewMode) {
        String combined = (jdTitle != null ? jdTitle : "") + "|" + 
                        (jdText != null ? jdText.substring(0, Math.min(500, jdText.length())) : "") + "|" +
                        (focusAreas != null ? focusAreas : "") + "|" +
                        (interviewMode != null ? interviewMode : "L3");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(combined.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(combined.hashCode());
        }
    }

    private boolean isExpired(Instant createdAt) {
        return createdAt.plusSeconds(TTL_HOURS * 3600).isBefore(Instant.now());
    }

    public void clearExpiredEntries() {
        cache.entrySet().removeIf(entry -> isExpired(entry.getValue().createdAt));
    }
}