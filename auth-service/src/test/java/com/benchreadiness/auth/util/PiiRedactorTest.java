package com.benchreadiness.auth.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PiiRedactorTest {

    @Test
    void masksEmailLocalPart() {
        assertEquals("j***@example.com", PiiRedactor.maskEmail("john@example.com"));
    }

    @Test
    void handlesNullAndBlank() {
        assertEquals("***", PiiRedactor.maskEmail(null));
        assertEquals("***", PiiRedactor.maskEmail(""));
    }
}
