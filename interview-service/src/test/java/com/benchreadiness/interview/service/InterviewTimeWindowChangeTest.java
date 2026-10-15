package com.benchreadiness.interview.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Report item 57: a reschedule mail goes out only when the time window really moves. */
class InterviewTimeWindowChangeTest {

    private static final Instant START = Instant.parse("2026-10-20T04:00:00Z");
    private static final Instant END = Instant.parse("2026-10-20T06:00:00Z");

    @Test
    void sameTimesResentByTheEditFormDoNotCount() {
        assertFalse(InterviewService.timeWindowChanged(START, START, END, END));
    }

    @Test
    void editWithoutTimeFieldsDoesNotCount() {
        assertFalse(InterviewService.timeWindowChanged(START, null, END, null));
    }

    @Test
    void movedStartCounts() {
        assertTrue(InterviewService.timeWindowChanged(START, START.plusSeconds(3600), END, END));
    }

    @Test
    void movedExpiryCounts() {
        assertTrue(InterviewService.timeWindowChanged(START, START, END, END.plusSeconds(60)));
    }

    @Test
    void firstTimeSetCounts() {
        assertTrue(InterviewService.timeWindowChanged(null, START, null, null));
    }
}
