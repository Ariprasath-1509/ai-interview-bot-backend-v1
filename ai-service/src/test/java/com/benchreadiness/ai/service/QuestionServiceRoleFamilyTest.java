package com.benchreadiness.ai.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the curated-pool role routing: a DevOps interview must never receive the
 * backend-developer curated questions (the "Spring Boot questions in a DevOps interview" bug).
 */
class QuestionServiceRoleFamilyTest {

    private final QuestionService service = new QuestionService(null, null);

    @Test
    void devOpsTitleMapsToDevOpsFamily() {
        assertEquals("DEVOPS", service.inferRoleFamily("DevOps Engineer", null));
        assertEquals("DEVOPS", service.inferRoleFamily("Senior Site Reliability Engineer", ""));
        assertEquals("DEVOPS", service.inferRoleFamily("Cloud Engineer - AWS", null));
    }

    @Test
    void backendTitlesKeepExistingPool() {
        assertEquals("BACKEND", service.inferRoleFamily("Senior Java Developer", null));
        assertEquals("BACKEND", service.inferRoleFamily("Backend Engineer", null));
        assertEquals("BACKEND", service.inferRoleFamily("Full Stack Developer", null));
    }

    @Test
    void genericTitleFallsThroughToJdText() {
        String devOpsJd = "We are looking for an engineer to own our Kubernetes clusters, "
            + "Terraform modules, and Jenkins pipelines. Experience with Prometheus and Grafana required.";
        assertEquals("DEVOPS", service.inferRoleFamily("Software Engineer", devOpsJd));

        String backendJd = "Design and build microservices using Spring Boot and Hibernate, "
            + "exposing REST API endpoints backed by Kafka.";
        assertEquals("BACKEND", service.inferRoleFamily("Software Engineer", backendJd));
    }

    @Test
    void ambiguousOrMissingContextStaysGeneric() {
        assertEquals("GENERIC", service.inferRoleFamily(null, null));
        assertEquals("GENERIC", service.inferRoleFamily("Target role", ""));
        assertEquals("GENERIC", service.inferRoleFamily("Engineer", "Great team, competitive salary."));
    }

    @Test
    void devOpsPoolContainsNoBackendDevQuestions() {
        Map<String, Map<Integer, String>> pool = service.curatedPoolForRole("DevOps Engineer", null);
        for (Map<Integer, String> modeQuestions : pool.values()) {
            for (String q : modeQuestions.values()) {
                String lower = q.toLowerCase();
                assertFalse(lower.contains("rest endpoint"), "DevOps pool leaked backend question: " + q);
                assertFalse(lower.contains("backend technology"), "DevOps pool leaked backend question: " + q);
                assertFalse(lower.contains("backend role"), "DevOps pool leaked backend question: " + q);
                assertFalse(lower.contains("monolith to microservices"), "DevOps pool leaked backend question: " + q);
            }
        }
    }

    @Test
    void poolsShareModeAndSlotStructureWithBackendPool() {
        Map<String, Map<Integer, String>> backend = service.curatedPoolForRole("Backend Engineer", null);
        Map<String, Map<Integer, String>> devops = service.curatedPoolForRole("DevOps Engineer", null);
        Map<String, Map<Integer, String>> generic = service.curatedPoolForRole("Underwater Basket Weaver", null);

        assertEquals(backend.keySet(), devops.keySet());
        assertEquals(backend.keySet(), generic.keySet());
        for (String mode : backend.keySet()) {
            assertEquals(backend.get(mode).keySet(), devops.get(mode).keySet(),
                "slot mismatch for mode " + mode);
            assertEquals(backend.get(mode).keySet(), generic.get(mode).keySet(),
                "slot mismatch for mode " + mode);
        }
    }

    @Test
    void explicitDifficultyOverridesModeDefault() {
        com.benchreadiness.ai.dto.NextQuestionRequest req = new com.benchreadiness.ai.dto.NextQuestionRequest();
        req.setInterviewMode("L4"); // mode default would be "hard difficulty"
        req.setQuestionDifficulty("EASY");
        assertEquals("easy difficulty", service.resolveDifficulty(req));

        req.setQuestionDifficulty("medium"); // case-insensitive
        assertEquals("medium difficulty", service.resolveDifficulty(req));
    }

    @Test
    void missingOrInvalidDifficultyFallsBackToMode() {
        com.benchreadiness.ai.dto.NextQuestionRequest req = new com.benchreadiness.ai.dto.NextQuestionRequest();
        req.setInterviewMode("L4");
        req.setQuestionDifficulty(null);
        assertEquals("hard difficulty", service.resolveDifficulty(req));

        req.setQuestionDifficulty("EXTREME"); // unknown value → mode default
        assertEquals("hard difficulty", service.resolveDifficulty(req));

        req.setInterviewMode(null); // no mode either → L3 default
        assertEquals("medium-hard difficulty", service.resolveDifficulty(req));
    }

    @Test
    void backendPoolIsTheOriginalUnchangedMap() {
        Map<String, Map<Integer, String>> pool = service.curatedPoolForRole("Java Developer", null);
        assertTrue(pool.get("L1").get(5).contains("REST endpoint"));
        assertSame(pool, service.curatedPoolForRole("Backend Engineer", "anything"));
    }
}
