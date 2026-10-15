package com.benchreadiness.ops.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Every mail of this service goes through here (report items 53 and 57):
 * <ul>
 *   <li>no duplicate for the same event, recipient and key inside MAIL_DEDUP_SECONDS (critical mails such as the
 *       password reset code skip this: their caller has its own cooldown);</li>
 *   <li>bulk sends are spaced to at most MAIL_MAX_PER_SECOND (critical mails skip the spacing);</li>
 *   <li>one outcome log line per mail: event, masked address, provider, message id.
 *       Never the body, a code or a password.</li>
 * </ul>
 * The duplicate window and the spacing are kept in memory, per service instance.
 */
@Component
public class MailGateway {

    private static final Logger log = LoggerFactory.getLogger(MailGateway.class);
    private static final int PRUNE_THRESHOLD = 5_000;

    private final MailTransport transport;
    private final long dedupMillis;
    private final long intervalMillis;
    private final Map<String, Long> recent = new ConcurrentHashMap<>();
    private final AtomicLong nextSlot = new AtomicLong(0);

    public MailGateway(MailTransport transport,
                       @Value("${app.mail.dedup-seconds:120}") long dedupSeconds,
                       @Value("${app.mail.max-per-second:5}") int maxPerSecond) {
        this.transport = transport;
        this.dedupMillis = Math.max(0, dedupSeconds) * 1000L;
        this.intervalMillis = maxPerSecond > 0 ? 1000L / maxPerSecond : 0;
    }

    public MailResult send(MailMessage message) {
        String to = message.to() == null ? "" : message.to().trim();
        String masked = maskEmail(to);
        if (to.isEmpty()) {
            return logged(message, masked, MailResult.failed("no recipient"));
        }

        String key = message.event() + "|" + to.toLowerCase(Locale.ROOT) + "|"
                + (message.dedupKey() == null ? "" : message.dedupKey());
        if (!message.critical() && !claim(key)) {
            return logged(message, masked, MailResult.duplicate());
        }

        MailResult result;
        try {
            if (!message.critical()) {
                throttle();
            }
            result = transport.deliver(message.withTo(to));
        } catch (RuntimeException e) {
            result = MailResult.failed(e.getClass().getSimpleName());
        }
        if (result.isFailed() && !message.critical()) {
            recent.remove(key); // a failed mail must be allowed to retry
        }
        return logged(message, masked, result);
    }

    private boolean claim(String key) {
        if (dedupMillis == 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (recent.size() > PRUNE_THRESHOLD) {
            recent.values().removeIf(t -> now - t >= dedupMillis);
        }
        boolean[] claimed = {false};
        recent.compute(key, (k, previous) -> {
            if (previous == null || now - previous >= dedupMillis) {
                claimed[0] = true;
                return now;
            }
            return previous;
        });
        return claimed[0];
    }

    private void throttle() {
        if (intervalMillis <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long next = nextSlot.updateAndGet(prev -> Math.max(prev, now) + intervalMillis);
        long wait = (next - intervalMillis) - now;
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private MailResult logged(MailMessage message, String masked, MailResult result) {
        switch (result.status()) {
            case SENT -> log.info("MAIL event={} outcome=SENT provider={} to={} messageId={}",
                    message.event(), transport.name(), masked, result.messageId());
            case DUPLICATE -> log.info("MAIL event={} outcome=DUPLICATE_SKIPPED provider={} to={}",
                    message.event(), transport.name(), masked);
            case FAILED -> log.warn("MAIL event={} outcome=FAILED provider={} to={} detail={}",
                    message.event(), transport.name(), masked, result.detail());
        }
        return result;
    }

    static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        return (local.length() == 1 ? "*" : local.charAt(0) + "***") + "@" + email.substring(at + 1);
    }
}
