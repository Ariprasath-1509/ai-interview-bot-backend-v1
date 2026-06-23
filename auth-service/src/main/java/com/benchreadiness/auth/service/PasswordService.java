package com.benchreadiness.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {

    private static final String BCRYPT_PREFIX = "{bcrypt}";
    private static final String NOOP_PREFIX = "{noop}";

    private final PasswordEncoder passwordEncoder;

    public PasswordService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * Verifies a raw password against a stored value. Supports BCrypt hashes and
     * legacy plaintext passwords during migration.
     */
    public boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }

        NormalizedPassword normalized = normalize(storedPassword);
        if (normalized.encoded()) {
            return passwordEncoder.matches(rawPassword, normalized.value());
        }
        return normalized.value().equals(rawPassword);
    }

    public boolean needsUpgrade(String storedPassword) {
        NormalizedPassword normalized = normalize(storedPassword);
        return normalized != null && !normalized.encoded();
    }

    private NormalizedPassword normalize(String storedPassword) {
        if (storedPassword == null) {
            return null;
        }

        String value = storedPassword.trim();
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                value = value.substring(1, value.length() - 1).trim();
            }
        }

        if (value.regionMatches(true, 0, BCRYPT_PREFIX, 0, BCRYPT_PREFIX.length())) {
            return new NormalizedPassword(value.substring(BCRYPT_PREFIX.length()), true);
        }
        if (value.regionMatches(true, 0, NOOP_PREFIX, 0, NOOP_PREFIX.length())) {
            return new NormalizedPassword(value.substring(NOOP_PREFIX.length()), false);
        }
        return new NormalizedPassword(value, isBcryptHash(value));
    }

    private boolean isBcryptHash(String value) {
        return value.startsWith("$2a$")
                || value.startsWith("$2b$")
                || value.startsWith("$2y$");
    }

    private record NormalizedPassword(String value, boolean encoded) {}
}
