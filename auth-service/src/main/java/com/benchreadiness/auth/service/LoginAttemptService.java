package com.benchreadiness.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    private final int maxAttempts;
    private final long lockoutSeconds;
    private final ConcurrentHashMap<String, AttemptRecord> attemptsByEmail = new ConcurrentHashMap<>();

    public LoginAttemptService(
            @Value("${app.auth.max-login-attempts:10}") int maxAttempts,
            @Value("${app.auth.lockout-seconds:900}") long lockoutSeconds) {
        this.maxAttempts = maxAttempts;
        this.lockoutSeconds = lockoutSeconds;
    }

    public boolean isLocked(String email) {
        AttemptRecord record = attemptsByEmail.get(normalize(email));
        if (record == null) {
            return false;
        }
        if (record.lockedUntil != null && Instant.now().isBefore(record.lockedUntil)) {
            return true;
        }
        if (record.lockedUntil != null && Instant.now().isAfter(record.lockedUntil)) {
            attemptsByEmail.remove(normalize(email));
        }
        return false;
    }

    public void recordFailure(String email) {
        String key = normalize(email);
        attemptsByEmail.compute(key, (k, existing) -> {
            AttemptRecord record = existing != null ? existing : new AttemptRecord();
            record.failedAttempts++;
            if (record.failedAttempts >= maxAttempts) {
                record.lockedUntil = Instant.now().plusSeconds(lockoutSeconds);
            }
            return record;
        });
    }

    public void recordSuccess(String email) {
        attemptsByEmail.remove(normalize(email));
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private static class AttemptRecord {
        private int failedAttempts;
        private Instant lockedUntil;
    }
}
