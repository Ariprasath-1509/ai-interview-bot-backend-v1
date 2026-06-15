package com.benchreadiness.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class PasswordServiceTest {

    private PasswordService passwordService;

    @BeforeEach
    void setUp() {
        passwordService = new PasswordService(new BCryptPasswordEncoder());
    }

    @Test
    void encodeAndMatchBcryptPassword() {
        String encoded = passwordService.encode("Test@2026");
        assertTrue(passwordService.matches("Test@2026", encoded));
        assertFalse(passwordService.matches("wrong", encoded));
        assertFalse(passwordService.needsUpgrade(encoded));
    }

    @Test
    void matchesLegacyPlaintextPassword() {
        assertTrue(passwordService.matches("legacyPass", "legacyPass"));
        assertFalse(passwordService.matches("wrong", "legacyPass"));
        assertTrue(passwordService.needsUpgrade("legacyPass"));
    }

    @Test
    void matchesSpringBcryptPrefixFormat() {
        String encoded = passwordService.encode("Admin@123");
        assertTrue(passwordService.matches("Admin@123", "{bcrypt}" + encoded));
        assertFalse(passwordService.needsUpgrade("{bcrypt}" + encoded));
    }

    @Test
    void matchesBcryptWithSurroundingWhitespaceAndQuotes() {
        String encoded = passwordService.encode("Admin@123");
        assertTrue(passwordService.matches("Admin@123", "  \"" + encoded + "\"  "));
    }

    @Test
    void matchesNoopPrefixFormat() {
        assertTrue(passwordService.matches("plain", "{noop}plain"));
        assertTrue(passwordService.needsUpgrade("{noop}plain"));
    }
}
