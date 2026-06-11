package com.benchreadiness.auth.util;

public final class PiiRedactor {

    private PiiRedactor() {}

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
