package com.benchreadiness.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-address cooldown for password-reset mails (report items 53 and 57): one reset mail per address
 * per window, so a repeated click or a script cannot burn the provider quota. In memory, like
 * LoginAttemptService: an auth-service restart clears it, which only ever allows one extra mail.
 */
@Component
public class OtpMailCooldown {

    private static final int PRUNE_THRESHOLD = 5_000;

    private final long cooldownMillis;
    private final Map<String, Long> lastSentAt = new ConcurrentHashMap<>();

    public OtpMailCooldown(@Value("${app.otp.mail-cooldown-seconds:60}") long cooldownSeconds) {
        this.cooldownMillis = Math.max(0, cooldownSeconds) * 1000L;
    }

    /** Claims the slot for this address. False when a reset mail went out inside the cooldown window. */
    public boolean tryAcquire(String email) {
        return tryAcquire(email, System.currentTimeMillis());
    }

    boolean tryAcquire(String email, long nowMillis) {
        if (cooldownMillis == 0) {
            return true;
        }
        if (lastSentAt.size() > PRUNE_THRESHOLD) {
            lastSentAt.values().removeIf(t -> nowMillis - t >= cooldownMillis);
        }
        String key = email.trim().toLowerCase(Locale.ROOT);
        boolean[] acquired = {false};
        lastSentAt.compute(key, (k, previous) -> {
            if (previous == null || nowMillis - previous >= cooldownMillis) {
                acquired[0] = true;
                return nowMillis;
            }
            return previous;
        });
        return acquired[0];
    }

    /** Gives the slot back when the mail could not be sent, so the user can try again straight away. */
    public void release(String email) {
        lastSentAt.remove(email.trim().toLowerCase(Locale.ROOT));
    }
}
