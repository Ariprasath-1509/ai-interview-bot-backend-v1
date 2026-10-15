package com.benchreadiness.auth.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PiiRedactorTest {

    @Test
    void masksEmailLocalPart() {
        assertEquals("j***@example.com", PiiRedactor.maskEmail("john@example.com"));
    }

    @Test
    void masksKeepingTwoCharacters() {
        assertEquals("jo***@example.com", PiiRedactor.maskEmailKeepTwo("john@example.com"));
        assertEquals("a***@example.com", PiiRedactor.maskEmailKeepTwo("ab@example.com"));
        assertEquals("***@example.com", PiiRedactor.maskEmailKeepTwo("a@example.com"));
        assertEquals("***", PiiRedactor.maskEmailKeepTwo("not-an-address"));
        assertEquals("***", PiiRedactor.maskEmailKeepTwo(null));
    }

    @Test
    void handlesNullAndBlank() {
        assertEquals("***", PiiRedactor.maskEmail(null));
        assertEquals("***", PiiRedactor.maskEmail(""));
    }
}
