package com.benchreadiness.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptServiceTest {

    private static final String EMAIL = "user.one@example.test";

    private MutableClock clock;
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        // 3 failures within 60 s lock for 120 s
        service = new LoginAttemptService(3, 120, 60, clock);
    }

    @Test
    void locksAfterMaxFailuresInsideWindowThenUnlocks() {
        service.recordFailure(EMAIL);
        service.recordFailure(EMAIL);
        assertFalse(service.isLocked(EMAIL));
        service.recordFailure(EMAIL);
        assertTrue(service.isLocked(EMAIL));

        clock.advance(119);
        assertTrue(service.isLocked(EMAIL));
        clock.advance(2);
        assertFalse(service.isLocked(EMAIL));
        assertEquals(0, service.trackedEntries());
    }

    @Test
    void failuresOutsideTheWindowDoNotAccumulate() {
        service.recordFailure(EMAIL);
        service.recordFailure(EMAIL);
        clock.advance(61);
        service.recordFailure(EMAIL);
        service.recordFailure(EMAIL);
        assertFalse(service.isLocked(EMAIL), "only 2 failures are inside the window");
        service.recordFailure(EMAIL);
        assertTrue(service.isLocked(EMAIL));
    }

    @Test
    void mistypesSpreadOverTimeNeverLock() {
        for (int i = 0; i < 20; i++) {
            service.recordFailure(EMAIL);
            clock.advance(40); // never 3 inside a 60 s window
        }
        assertFalse(service.isLocked(EMAIL));
    }

    @Test
    void successClearsFailures() {
        service.recordFailure(EMAIL);
        service.recordFailure(EMAIL);
        service.recordSuccess(EMAIL);
        service.recordFailure(EMAIL);
        service.recordFailure(EMAIL);
        assertFalse(service.isLocked(EMAIL));
    }

    @Test
    void emailIsCaseAndWhitespaceInsensitive() {
        service.recordFailure(" USER.One@Example.test ");
        service.recordFailure(EMAIL);
        service.recordFailure("user.one@example.test");
        assertTrue(service.isLocked("USER.ONE@EXAMPLE.TEST"));
    }

    @Test
    void unknownEmailLocksLikeARealOneSoResponsesDoNotDiffer() {
        for (int i = 0; i < 3; i++) {
            service.recordUnknownAccountFailure("nobody@example.test");
        }
        assertTrue(service.isLocked("nobody@example.test"));
    }

    @Test
    void unknownEmailsDoNotTouchTheKnownMapAndAreBounded() {
        for (int i = 0; i < LoginAttemptService.MAX_UNKNOWN_ENTRIES + 500; i++) {
            service.recordUnknownAccountFailure("rand" + i + "@example.test");
            clock.advance(0);
        }
        assertTrue(service.trackedEntries() <= LoginAttemptService.MAX_UNKNOWN_ENTRIES,
                "unknown map must stay under its cap, was " + service.trackedEntries());
    }

    @Test
    void expiredEntriesAreSweptOnLaterWrites() {
        service.recordFailure("a@example.test");
        service.recordUnknownAccountFailure("b@example.test");
        assertEquals(2, service.trackedEntries());
        clock.advance(61 + 60); // past the window and the sweep interval
        service.recordFailure("c@example.test");
        assertEquals(1, service.trackedEntries(), "a and b expired and were removed");
    }

    @Test
    void expiredLockIsSweptWithoutAnyLoginForThatEmail() {
        for (int i = 0; i < 3; i++) {
            service.recordFailure(EMAIL);
        }
        clock.advance(121 + 60);
        service.recordFailure("other@example.test");
        assertEquals(1, service.trackedEntries());
    }

    /** Test clock that only moves when told to. */
    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(long seconds) {
            now = now.plus(Duration.ofSeconds(seconds));
        }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
