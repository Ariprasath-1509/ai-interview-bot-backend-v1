package com.benchreadiness.ai.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssessmentRequestTest {

    @Test
    void isOnboardingOnlyTrueForExactMatch() {
        AssessmentRequest req = new AssessmentRequest();
        assertFalse(req.isOnboarding());

        req.setAssessmentType("CLIENT_INTERVIEW");
        assertFalse(req.isOnboarding());

        req.setAssessmentType("onboarding"); // wrong case — normalization happens upstream in interview-service
        assertFalse(req.isOnboarding());

        req.setAssessmentType("ONBOARDING");
        assertTrue(req.isOnboarding());
    }
}
