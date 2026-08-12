package com.benchreadiness.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies the screening-checklist rating scale is driven by the checklist's own min/max/levels
 * instead of being hardcoded to 1-5, across parsing, verdict computation, and the fallback path
 * for checklists that don't define a scale at all.
 */
class ChecklistRatingScaleTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AssessmentService assessmentService =
        new AssessmentService(null, null, null, null, objectMapper);
    private final RubricService rubricService =
        new RubricService(null, objectMapper, null);

    @SuppressWarnings("unchecked")
    private <T> T invokePrivate(Object target, String methodName, Class<?>[] paramTypes, Object[] args) throws Exception {
        Method m = target.getClass().getDeclaredMethod(methodName, paramTypes);
        m.setAccessible(true);
        return (T) m.invoke(target, args);
    }

    @Test
    void parsesExplicit1To10ScaleFromChecklist() throws Exception {
        String rubricJson = """
            {
              "screeningChecklist": {
                "proceedGates": [],
                "rejectSignals": [],
                "validateFlags": [],
                "knockoutQuestions": [],
                "recommendationBands": [{"min": 8.0, "max": 10.0, "label": "Strong proceed", "action": "Move forward"}],
                "ratingScale": {
                  "min": 1,
                  "max": 10,
                  "levels": [
                    {"score": 1, "definition": "No evidence"},
                    {"score": 10, "definition": "Expert-level"}
                  ]
                }
              }
            }
            """;

        Map<String, Object> checklist = invokePrivate(assessmentService, "parseScreeningChecklist",
            new Class<?>[] {String.class}, new Object[] {rubricJson});

        assertNotNull(checklist);
        Map<String, Object> ratingScale = (Map<String, Object>) checklist.get("ratingScale");
        assertEquals(1, ((Number) ratingScale.get("min")).intValue());
        assertEquals(10, ((Number) ratingScale.get("max")).intValue());
        List<Map<String, Object>> levels = (List<Map<String, Object>>) ratingScale.get("levels");
        assertEquals(2, levels.size());
    }

    @Test
    void fallsBackTo1To5WhenChecklistOmitsRatingScale() throws Exception {
        String rubricJson = """
            {
              "screeningChecklist": {
                "proceedGates": [],
                "rejectSignals": [],
                "validateFlags": [],
                "knockoutQuestions": [],
                "recommendationBands": [{"min": 4.0, "max": 5.0, "label": "Strong proceed", "action": "Move forward"}]
              }
            }
            """;

        Map<String, Object> checklist = invokePrivate(assessmentService, "parseScreeningChecklist",
            new Class<?>[] {String.class}, new Object[] {rubricJson});

        Map<String, Object> ratingScale = (Map<String, Object>) checklist.get("ratingScale");
        assertEquals(1, ((Number) ratingScale.get("min")).intValue());
        assertEquals(5, ((Number) ratingScale.get("max")).intValue());
    }

    @Test
    void applyChecklistVerdictUsesChecklistScaleMaxNotHardcodedFive() throws Exception {
        // Two dimensions, weighted 50/50, scored on the checklist's own 1-10 scale.
        List<Map<String, Object>> categories = List.of(
            Map.of("key", "dimA", "label", "Dimension A", "weightPct", 50.0),
            Map.of("key", "dimB", "label", "Dimension B", "weightPct", 50.0)
        );

        Map<String, Object> checklist = Map.of(
            "recommendationBands", List.of(
                Map.of("min", 8.0, "max", 10.0, "label", "Strong proceed", "action", "Move forward")
            ),
            "ratingScale", Map.of(
                "min", 1, "max", 10,
                "levels", List.of(Map.of("score", 1, "definition", "No evidence"))
            )
        );

        // categoryScores rows as produced by aggregateStages: dimension/value pairs.
        List<Map<String, Object>> categoryScoreRows = List.of(
            Map.of("dimension", "dimA", "value", "9"),
            Map.of("dimension", "dimB", "value", "9")
        );
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("categoryScores", categoryScoreRows);

        String scoresJson = "{\"rejectSignalsTriggered\":[],\"proceedGatesMet\":[],\"validateFlagsTriggered\":[],\"knockoutQuestionsAsked\":[]}";
        JsonNode scores = objectMapper.readTree(scoresJson);

        Method m = AssessmentService.class.getDeclaredMethod("applyChecklistVerdict",
            Map.class, List.class, Map.class, JsonNode.class);
        m.setAccessible(true);
        m.invoke(assessmentService, result, categories, checklist, scores);

        // scoreMax must reflect the checklist's own 1-10 scale, not a hardcoded 5.
        assertEquals(10, ((Number) result.get("scoreMax")).intValue());

        Map<String, Object> checklistResult = (Map<String, Object>) result.get("screeningChecklistResult");
        assertEquals(10.0, ((Number) checklistResult.get("scoreMax")).doubleValue());
        // weightedTotal = (9*50 + 9*50) / 100 = 9.0, correctly on the 1-10 scale (not clamped to 5).
        assertEquals(9.0, ((Number) checklistResult.get("weightedTotal")).doubleValue());
        assertEquals("Strong proceed", checklistResult.get("band"));
        assertEquals("READY", result.get("proposedVerdict"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void rubricServiceDefaultsRatingScaleWhenAbsentAndPreservesCustomOne() throws Exception {
        JsonNode absent = objectMapper.readTree("{}").path("ratingScale");
        Map<String, Object> defaulted = invokePrivate(rubricService, "resolveRatingScale",
            new Class<?>[] {JsonNode.class}, new Object[] {absent});
        assertEquals(1, ((Number) defaulted.get("min")).intValue());
        assertEquals(5, ((Number) defaulted.get("max")).intValue());
        assertEquals(5, ((List<?>) defaulted.get("levels")).size());

        JsonNode custom = objectMapper.readTree("""
            {"min": 0, "max": 7, "levels": [{"score": 0, "definition": "None"}, {"score": 7, "definition": "Expert"}]}
            """);
        Map<String, Object> preserved = invokePrivate(rubricService, "resolveRatingScale",
            new Class<?>[] {JsonNode.class}, new Object[] {custom});
        assertEquals(0, ((Number) preserved.get("min")).intValue());
        assertEquals(7, ((Number) preserved.get("max")).intValue());
        assertEquals(2, ((List<?>) preserved.get("levels")).size());
    }
}
