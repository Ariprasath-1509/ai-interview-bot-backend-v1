package com.benchreadiness.auth.util;

public final class PiiRedactor {

    private PiiRedactor() {}

    /**
     * Keeps the first two characters of the local part (one fewer if the local part is too short
     * to hide anything) and the domain, e.g. {@code ab***@gmail.com}. Used for login-failure lines.
     */
    public static String maskEmailKeepTwo(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        String trimmed = email.trim();
        int at = trimmed.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = trimmed.substring(0, at);
        String domain = trimmed.substring(at + 1);
        int keep = Math.min(2, local.length() - 1);
        return (keep > 0 ? local.substring(0, keep) : "") + "***@" + domain;
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        String maskedLocal = local.length() == 1 ? "*" : local.charAt(0) + "***";
        return maskedLocal + "@" + domain;
    }
}
