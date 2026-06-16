package com.benchreadiness.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TutoringServiceTest {

    @Test
    void parseQuestionTutorials_parsesValidJson() throws Exception {
        String raw = """
            {
              "questionTutorials": [
                {
                  "slotNumber": 1,
                  "questionType": "TECHNICAL",
                  "tags": ["Java", "Spring"],
                  "question": "Explain dependency injection?",
                  "candidateAnswer": "DI gives objects dependencies externally.",
                  "expectedAnswer": "At 3 YOE, cover IoC, constructor vs field injection, and one example.",
                  "tutorNote": "Good start — add injection types.",
                  "coverage": "partial"
                }
              ]
            }
            """;

        List<TutoringService.QuestionSlot> slots = List.of(
                new TutoringService.QuestionSlot(1, "Explain dependency injection?", "DI gives objects dependencies externally.",
                        List.of("Java"), "TECHNICAL", null)
        );

        List<Map<String, Object>> parsed = TutoringService.parseQuestionTutorials(raw, slots);
        assertEquals(1, parsed.size());
        assertEquals(1, parsed.get(0).get("slotNumber"));
        assertEquals("partial", parsed.get(0).get("coverage"));
        assertEquals("TECHNICAL", parsed.get(0).get("questionType"));
        assertTrue(parsed.get(0).get("expectedAnswer").toString().contains("YOE"));
    }

    @Test
    void parseQuestionTutorials_repairsMalformedJsonWithFallback() throws Exception {
        List<TutoringService.QuestionSlot> slots = List.of(
                new TutoringService.QuestionSlot(2, "What is REST?", "",
                        List.of(), "TECHNICAL", null)
        );

        List<Map<String, Object>> parsed = TutoringService.parseQuestionTutorials("not json at all", slots);
        assertEquals(1, parsed.size());
        assertEquals(2, parsed.get(0).get("slotNumber"));
        assertEquals("missing", parsed.get(0).get("coverage"));
        assertEquals("(no answer)", parsed.get(0).get("candidateAnswer"));
    }

    @Test
    void parseQuestionTutorials_normalizesInvalidCoverage() throws Exception {
        String raw = """
            {"questionTutorials":[{"slotNumber":1,"questionType":"TECHNICAL","tags":[],"question":"Q","candidateAnswer":"A","expectedAnswer":"E","tutorNote":"N","coverage":"UNKNOWN"}]}
            """;
        List<Map<String, Object>> parsed = TutoringService.parseQuestionTutorials(raw, List.of());
        assertEquals("partial", parsed.get(0).get("coverage"));
    }

    @Test
    void pairFromTranscript_pairsBotAndCandidateTurns() {
        List<Map<String, String>> utterances = List.of(
                Map.of("speaker", "BOT", "text", "Tell me about Spring Boot."),
                Map.of("speaker", "CANDIDATE", "text", "Spring Boot simplifies configuration."),
                Map.of("speaker", "BOT", "text", "How do you handle errors in REST APIs?"),
                Map.of("speaker", "CANDIDATE", "text", "I use @ControllerAdvice and proper HTTP status codes.")
        );

        List<TutoringService.QuestionSlot> slots = TutoringService.pairFromTranscript(utterances);
        assertEquals(2, slots.size());
        assertEquals(1, slots.get(0).slotNumber());
        assertEquals("Tell me about Spring Boot.", slots.get(0).question());
        assertEquals("Spring Boot simplifies configuration.", slots.get(0).candidateAnswer());
        assertEquals("TECHNICAL", slots.get(0).questionType());
    }

    @Test
    void pairFromTranscript_includesUnansweredFinalQuestion() {
        List<Map<String, String>> utterances = List.of(
                Map.of("speaker", "BOT", "text", "Describe a challenging bug you fixed."),
                Map.of("speaker", "CANDIDATE", "text", "We had a memory leak in production."),
                Map.of("speaker", "BOT", "text", "Any questions for us?")
        );

        List<TutoringService.QuestionSlot> slots = TutoringService.pairFromTranscript(utterances);
        assertEquals(2, slots.size());
        assertEquals("", slots.get(1).candidateAnswer());
        assertEquals("Any questions for us?", slots.get(1).question());
    }

    @Test
    void indexCodeSubmissionsBySlot_mapsArrayEntries() throws Exception {
        String json = new ObjectMapper().writeValueAsString(List.of(
                Map.of("slotNumber", 3, "language", "java", "code", "class Main {}"),
                Map.of("slot", 5, "language", "python", "code", "print('hi')")
        ));

        Map<Integer, String> indexed = TutoringService.indexCodeSubmissionsBySlot(json);
        assertTrue(indexed.containsKey(3));
        assertTrue(indexed.containsKey(5));
        assertTrue(indexed.get(3).contains("java"));
    }

    @Test
    void buildCategoryGaps_extractsNonEmptyGaps() throws Exception {
        String scoresJson = """
            {"categoryScores":{"coreJava":{"score":3,"gap":"thread safety and concurrency"},"spring":{"score":5,"gap":"none"}}}
            """;
        var node = new ObjectMapper().readTree(scoresJson);
        List<Map<String, Object>> categories = List.of(
                Map.of("key", "coreJava"),
                Map.of("key", "spring")
        );

        Map<String, String> gaps = TutoringService.buildCategoryGaps(node, categories);
        assertEquals(1, gaps.size());
        assertTrue(gaps.containsKey("coreJava"));
        assertTrue(gaps.get("coreJava").contains("thread safety"));
    }

    @Test
    void parseQuestionTutorials_repairsTrailingCommaJson() throws Exception {
        String raw = """
            {
              "questionTutorials": [
                {
                  "slotNumber": 1,
                  "questionType": "TECHNICAL",
                  "tags": ["Java"],
                  "question": "Q?",
                  "candidateAnswer": "A",
                  "expectedAnswer": "E",
                  "tutorNote": "N",
                  "coverage": "strong",
                },
              ],
            }
            """;
        List<Map<String, Object>> parsed = TutoringService.parseQuestionTutorials(raw, List.of());
        assertEquals(1, parsed.size());
        assertEquals("strong", parsed.get(0).get("coverage"));
    }

    @Test
    void buildTutoringSystemPrompt_includesLevelAndYoe() {
        String prompt = TutoringService.buildTutoringSystemPrompt("mid", "3");
        assertTrue(prompt.contains("mid"));
        assertTrue(prompt.contains("3 YOE"));
        assertTrue(prompt.contains("questionTutorials"));
    }
}
