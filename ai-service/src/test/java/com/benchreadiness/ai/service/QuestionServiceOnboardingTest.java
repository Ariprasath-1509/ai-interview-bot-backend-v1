package com.benchreadiness.ai.service;

import com.benchreadiness.ai.dto.NextQuestionRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the ONBOARDING assessment type: question generation must stay concept-scoped and never
 * fall back to the role/JD-flavored curated pools built for client interviews.
 */
class QuestionServiceOnboardingTest {

    private final QuestionService service = new QuestionService(null, null);

    private NextQuestionRequest onboardingRequest(String concept, int slot) {
        NextQuestionRequest req = new NextQuestionRequest();
        req.setAssessmentType("ONBOARDING");
        req.setJdTitle(concept);
        req.setSlot(slot);
        return req;
    }

    @Test
    void isOnboardingFlagReadsAssessmentType() {
        NextQuestionRequest req = new NextQuestionRequest();
        assertFalse(req.isOnboarding());
        req.setAssessmentType("CLIENT_INTERVIEW");
        assertFalse(req.isOnboarding());
        req.setAssessmentType("ONBOARDING");
        assertTrue(req.isOnboarding());
    }

    @Test
    void onboardingFallbackQuestionsMentionTheStatedConcept() {
        NextQuestionRequest req = onboardingRequest("Java Collections", 1);
        String question = service.pickFreshOnboardingQuestion(req, List.of());
        assertTrue(question.contains("Java Collections"), "Fallback question should reference the concept: " + question);
    }

    @Test
    void onboardingFallbackAvoidsRepeatingUsedQuestions() {
        NextQuestionRequest req = onboardingRequest("REST APIs", 1);
        String first = service.pickFreshOnboardingQuestion(req, List.of());
        String second = service.pickFreshOnboardingQuestion(req, List.of(first));
        assertFalse(second.equalsIgnoreCase(first), "Second pick should differ once the first is marked used");
    }

    @Test
    void onboardingFallbackNeverProducesRoleFlavoredQuestions() {
        NextQuestionRequest req = onboardingRequest("Exception Handling", 1);
        for (int i = 0; i < 5; i++) {
            String q = service.pickFreshOnboardingQuestion(req, List.of());
            String lower = q.toLowerCase();
            assertFalse(lower.contains("production"), "Onboarding fallback leaked role-flavored wording: " + q);
            assertFalse(lower.contains("architecture"), "Onboarding fallback leaked role-flavored wording: " + q);
        }
    }
}
