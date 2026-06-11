package com.benchreadiness.gateway;

import org.springframework.http.HttpHeaders;

import java.util.Set;

public final class LogRedaction {

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            HttpHeaders.AUTHORIZATION.toLowerCase(),
            "cookie",
            "x-gateway-key",
            "set-cookie"
    );

    private LogRedaction() {}

    public static String redactHeaders(HttpHeaders headers) {
        StringBuilder builder = new StringBuilder("[");
        headers.forEach((name, values) -> {
            if (SENSITIVE_HEADERS.contains(name.toLowerCase())) {
                builder.append(name).append("=[REDACTED], ");
            } else {
                builder.append(name).append("=").append(values).append(", ");
            }
        });
        if (builder.length() > 1) {
            builder.setLength(builder.length() - 2);
        }
        builder.append("]");
        return builder.toString();
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return "***";
        }
        int at = email.indexOf('@');
        return email.charAt(0) + "***" + email.substring(at);
    }
}
