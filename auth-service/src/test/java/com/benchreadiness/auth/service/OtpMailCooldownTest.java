package com.benchreadiness.auth.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OtpMailCooldownTest {

    @Test
    void secondRequestInsideTheWindowIsRefused() {
        OtpMailCooldown cooldown = new OtpMailCooldown(60);
        assertTrue(cooldown.tryAcquire("user@example.test", 1_000));
        assertFalse(cooldown.tryAcquire("user@example.test", 30_000));
    }

    @Test
    void addressIsComparedWithoutCaseOrSpaces() {
        OtpMailCooldown cooldown = new OtpMailCooldown(60);
        assertTrue(cooldown.tryAcquire("User@Example.test", 1_000));
        assertFalse(cooldown.tryAcquire(" user@example.TEST ", 2_000));
    }

    @Test
    void requestAfterTheWindowIsAllowed() {
        OtpMailCooldown cooldown = new OtpMailCooldown(60);
        assertTrue(cooldown.tryAcquire("user@example.test", 1_000));
        assertTrue(cooldown.tryAcquire("user@example.test", 61_000));
    }

    @Test
    void otherAddressesAreNotAffected() {
        OtpMailCooldown cooldown = new OtpMailCooldown(60);
        assertTrue(cooldown.tryAcquire("a@example.test", 1_000));
        assertTrue(cooldown.tryAcquire("b@example.test", 1_000));
    }

    @Test
    void releaseLetsTheUserTryAgainAfterAFailedSend() {
        OtpMailCooldown cooldown = new OtpMailCooldown(60);
        assertTrue(cooldown.tryAcquire("user@example.test", 1_000));
        cooldown.release("user@example.test");
        assertTrue(cooldown.tryAcquire("user@example.test", 2_000));
    }

    @Test
    void zeroSecondsDisablesTheCooldown() {
        OtpMailCooldown cooldown = new OtpMailCooldown(0);
        assertTrue(cooldown.tryAcquire("user@example.test", 1_000));
        assertTrue(cooldown.tryAcquire("user@example.test", 1_001));
    }
}
