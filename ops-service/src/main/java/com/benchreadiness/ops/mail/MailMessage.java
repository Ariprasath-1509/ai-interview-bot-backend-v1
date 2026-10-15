package com.benchreadiness.ops.mail;

/**
 * One outgoing mail.
 *
 * @param event     short event name used in the outcome log, e.g. CANDIDATE_CREDENTIALS
 * @param dedupKey  extra part of the duplicate key (e.g. an interview id), or null: the same event and recipient
 *                  inside the duplicate window then count as the same mail
 * @param critical  skips the bulk throttle and the duplicate window (password reset codes must not queue behind a
 *                  bulk send, and every new code must reach the user)
 */
public record MailMessage(String event, String to, String subject, String html, String dedupKey, boolean critical) {

    public static MailMessage of(String event, String to, String subject, String html) {
        return new MailMessage(event, to, subject, html, null, false);
    }

    public MailMessage withDedupKey(String key) {
        return new MailMessage(event, to, subject, html, key, critical);
    }

    public MailMessage asCritical() {
        return new MailMessage(event, to, subject, html, dedupKey, true);
    }

    MailMessage withTo(String newTo) {
        return new MailMessage(event, newTo, subject, html, dedupKey, critical);
    }
}
