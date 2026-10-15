package com.benchreadiness.auth.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory login lockout. Only failures inside the sliding attempt window count toward the
 * lock; a lock lasts {@code lockoutSeconds}. Unknown emails are tracked in a separate, size-capped
 * map so they lock exactly like real accounts (no user-enumeration signal) without unbounded growth.
 */
@Service
public class LoginAttemptService {

    static final int MAX_UNKNOWN_ENTRIES = 10_000;
    private static final int MAX_KEY_LENGTH = 254;
    private static final long SWEEP_INTERVAL_SECONDS = 60;

    private final int maxAttempts;
    private final long lockoutSeconds;
    private final long windowSeconds;
    private final Clock clock;
    private final ConcurrentHashMap<String, AttemptRecord> knownAttempts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AttemptRecord> unknownAttempts = new ConcurrentHashMap<>();
    private volatile Instant lastSweep;

    @Autowired
    public LoginAttemptService(
            @Value("${app.auth.max-login-attempts:10}") int maxAttempts,
            @Value("${app.auth.lockout-seconds:900}") long lockoutSeconds,
            @Value("${app.auth.attempt-window-seconds:900}") long windowSeconds) {
        this(maxAttempts, lockoutSeconds, windowSeconds, Clock.systemUTC());
    }

    LoginAttemptService(int maxAttempts, long lockoutSeconds, long windowSeconds, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.lockoutSeconds = lockoutSeconds;
        this.windowSeconds = windowSeconds;
        this.clock = clock;
        this.lastSweep = clock.instant();
    }

    public boolean isLocked(String email) {
        String key = normalize(email);
        Instant now = clock.instant();
        return isLocked(knownAttempts, key, now) || isLocked(unknownAttempts, key, now);
    }

    /** Records a failed login for an existing account. */
    public void recordFailure(String email) {
        record(knownAttempts, email);
    }

    /** Records a failed login for an email with no account (kept in a bounded map). */
    public void recordUnknownAccountFailure(String email) {
        record(unknownAttempts, email);
        if (unknownAttempts.size() > MAX_UNKNOWN_ENTRIES) {
            evictUnknownOverflow();
        }
    }

    public void recordSuccess(String email) {
        String key = normalize(email);
        knownAttempts.remove(key);
        unknownAttempts.remove(key);
    }

    int trackedEntries() {
        return knownAttempts.size() + unknownAttempts.size();
    }

    private boolean isLocked(ConcurrentHashMap<String, AttemptRecord> map, String key, Instant now) {
        AttemptRecord record = map.get(key);
        if (record == null) {
            return false;
        }
        synchronized (record) {
            if (record.lockedUntil != null) {
                if (now.isBefore(record.lockedUntil)) {
                    return true;
                }
                map.remove(key, record);
            }
        }
        return false;
    }

    private void record(ConcurrentHashMap<String, AttemptRecord> map, String email) {
        Instant now = clock.instant();
        sweepIfDue(now);
        map.compute(normalize(email), (k, existing) -> {
            AttemptRecord record = existing != null ? existing : new AttemptRecord();
            synchronized (record) {
                if (record.lockedUntil != null && !now.isBefore(record.lockedUntil)) {
                    record.lockedUntil = null;
                    record.failures.clear();
                }
                prune(record, now);
                record.failures.addLast(now);
                if (record.failures.size() >= maxAttempts) {
                    record.lockedUntil = now.plusSeconds(lockoutSeconds);
                    record.failures.clear();
                }
            }
            return record;
        });
    }

    private void prune(AttemptRecord record, Instant now) {
        Instant cutoff = now.minusSeconds(windowSeconds);
        while (!record.failures.isEmpty() && !record.failures.peekFirst().isAfter(cutoff)) {
            record.failures.removeFirst();
        }
    }

    private void sweepIfDue(Instant now) {
        if (now.isBefore(lastSweep.plusSeconds(SWEEP_INTERVAL_SECONDS))) {
            return;
        }
        lastSweep = now;
        sweep(knownAttempts, now);
        sweep(unknownAttempts, now);
    }

    private void sweep(ConcurrentHashMap<String, AttemptRecord> map, Instant now) {
        for (Iterator<java.util.Map.Entry<String, AttemptRecord>> it = map.entrySet().iterator(); it.hasNext(); ) {
            java.util.Map.Entry<String, AttemptRecord> entry = it.next();
            AttemptRecord record = entry.getValue();
            boolean remove;
            synchronized (record) {
                if (record.lockedUntil != null && !now.isBefore(record.lockedUntil)) {
                    remove = true;
                } else {
                    prune(record, now);
                    remove = record.lockedUntil == null && record.failures.isEmpty();
                }
            }
            if (remove) {
                map.remove(entry.getKey(), record);
            }
        }
    }

    /** Hard cap: drop the entries with the oldest activity until the map is back under the limit. */
    private void evictUnknownOverflow() {
        Instant now = clock.instant();
        sweep(unknownAttempts, now);
        if (unknownAttempts.size() <= MAX_UNKNOWN_ENTRIES) {
            return;
        }
        // evict down to 90% in one pass so a flood at the cap does not sort the map on every request
        int excess = unknownAttempts.size() - MAX_UNKNOWN_ENTRIES * 9 / 10;
        if (excess <= 0) {
            return;
        }
        unknownAttempts.entrySet().stream()
                .sorted(java.util.Comparator.comparing(e -> e.getValue().lastActivity()))
                .limit(excess)
                .map(java.util.Map.Entry::getKey)
                .toList()
                .forEach(unknownAttempts::remove);
    }

    private String normalize(String email) {
        if (email == null) {
            return "";
        }
        String key = email.trim().toLowerCase();
        return key.length() > MAX_KEY_LENGTH ? key.substring(0, MAX_KEY_LENGTH) : key;
    }

    private static class AttemptRecord {
        private final Deque<Instant> failures = new ArrayDeque<>();
        private Instant lockedUntil;

        private Instant lastActivity() {
            synchronized (this) {
                if (lockedUntil != null) {
                    return lockedUntil;
                }
                return failures.isEmpty() ? Instant.EPOCH : failures.peekLast();
            }
        }
    }
}
