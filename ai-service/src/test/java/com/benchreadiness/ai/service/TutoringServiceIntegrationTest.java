package com.benchreadiness.ai.service;

import com.benchreadiness.ai.client.InterviewServiceClient;
import com.benchreadiness.ai.dto.AssessmentRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TutoringServiceIntegrationTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private InterviewServiceClient interviewServiceClient;

    @InjectMocks
    private TutoringService tutoringService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void attachQuestionTutorials_mergesFromInterviewQuestions() throws Exception {
        when(llmClient.isConfigured()).thenReturn(true);
        when(interviewServiceClient.getInterviewQuestions("iv-1")).thenReturn(List.of(
                Map.of(
                        "slotNumber", 1,
                        "questionText", "What is dependency injection?",
                        "candidateAnswer", "Giving dependencies from outside",
                        "questionType", "TECHNICAL",
                        "tags", List.of("Spring")
                )
        ));
        when(llmClient.chatAssessmentWithTracking(anyString(), anyString(), eq("iv-1"), eq("stage5-tutoring")))
                .thenReturn("""
                    {
                      "questionTutorials": [{
                        "slotNumber": 1,
                        "questionType": "TECHNICAL",
                        "tags": ["Spring"],
                        "question": "What is dependency injection?",
                        "candidateAnswer": "Giving dependencies from outside",
                        "expectedAnswer": "At mid / 3 YOE, explain IoC and constructor injection.",
                        "tutorNote": "Mention when to prefer constructor injection.",
                        "coverage": "partial"
                      }]
                    }
                    """);

        AssessmentRequest req = buildRequest("iv-1");
        Map<String, Object> result = resultWithFeedback();
        JsonNode scoresJson = objectMapper.readTree("""
            {"categoryScores":{"spring":{"score":3,"gap":"DI and bean lifecycle"}}}
            """);
        List<Map<String, Object>> categories = List.of(Map.of("key", "spring"));

        tutoringService.attachQuestionTutorials(result, req, scoresJson, categories, List.of(), "test-user");

        @SuppressWarnings("unchecked")
        Map<String, Object> feedback = (Map<String, Object>) result.get("candidateFeedback");
        assertEquals("COMPLETED", feedback.get("tutoringStatus"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tutorials = (List<Map<String, Object>>) feedback.get("questionTutorials");
        assertEquals(1, tutorials.size());
        assertEquals("partial", tutorials.get(0).get("coverage"));
        assertTrue(tutorials.get(0).get("expectedAnswer").toString().contains("YOE"));
    }

    @Test
    void attachQuestionTutorials_usesTranscriptFallbackWhenQuestionsEmpty() throws Exception {
        when(llmClient.isConfigured()).thenReturn(true);
        when(interviewServiceClient.getInterviewQuestions("iv-2")).thenReturn(List.of());
        when(llmClient.chatAssessmentWithTracking(anyString(), anyString(), eq("iv-2"), eq("stage5-tutoring")))
                .thenReturn("""
                    {
                      "questionTutorials": [{
                        "slotNumber": 1,
                        "questionType": "TECHNICAL",
                        "tags": [],
                        "question": "Explain REST.",
                        "candidateAnswer": "HTTP APIs",
                        "expectedAnswer": "Resources, verbs, status codes.",
                        "tutorNote": "Add idempotency example.",
                        "coverage": "weak"
                      }]
                    }
                    """);

        List<Map<String, String>> utterances = List.of(
                Map.of("speaker", "BOT", "text", "Explain REST."),
                Map.of("speaker", "CANDIDATE", "text", "HTTP APIs")
        );

        AssessmentRequest req = buildRequest("iv-2");
        Map<String, Object> result = resultWithFeedback();

        tutoringService.attachQuestionTutorials(
                result, req, objectMapper.createObjectNode(), List.of(), utterances, "test-user");

        @SuppressWarnings("unchecked")
        Map<String, Object> feedback = (Map<String, Object>) result.get("candidateFeedback");
        assertEquals("COMPLETED", feedback.get("tutoringStatus"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tutorials = (List<Map<String, Object>>) feedback.get("questionTutorials");
        assertEquals(1, tutorials.size());
        assertEquals("weak", tutorials.get(0).get("coverage"));
    }

    @Test
    void attachQuestionTutorials_llmFailure_setsFailedWithoutRemovingExistingFeedback() throws Exception {
        when(llmClient.isConfigured()).thenReturn(true);
        when(interviewServiceClient.getInterviewQuestions("iv-3")).thenReturn(List.of(
                Map.of("slotNumber", 1, "questionText", "Q?", "candidateAnswer", "A")
        ));
        when(llmClient.chatAssessmentWithTracking(anyString(), anyString(), eq("iv-3"), eq("stage5-tutoring")))
                .thenThrow(new RuntimeException("token limit"));

        AssessmentRequest req = buildRequest("iv-3");
        Map<String, Object> result = resultWithFeedback();

        tutoringService.attachQuestionTutorials(
                result, req, objectMapper.createObjectNode(), List.of(), List.of(), "test-user");

        @SuppressWarnings("unchecked")
        Map<String, Object> feedback = (Map<String, Object>) result.get("candidateFeedback");
        assertEquals("FAILED", feedback.get("tutoringStatus"));
        assertEquals("Good effort", feedback.get("summary"));
        assertNull(feedback.get("questionTutorials"));
    }

    @Test
    void attachQuestionTutorials_skippedWhenLlmNotConfigured() {
        when(llmClient.isConfigured()).thenReturn(false);

        AssessmentRequest req = buildRequest("iv-4");
        Map<String, Object> result = resultWithFeedback();

        tutoringService.attachQuestionTutorials(
                result, req, objectMapper.createObjectNode(), List.of(), List.of(), "test-user");

        @SuppressWarnings("unchecked")
        Map<String, Object> feedback = (Map<String, Object>) result.get("candidateFeedback");
        assertEquals("SKIPPED", feedback.get("tutoringStatus"));
        assertEquals(List.of(), feedback.get("questionTutorials"));
    }

    private AssessmentRequest buildRequest(String interviewId) {
        AssessmentRequest req = new AssessmentRequest();
        req.setInterviewId(interviewId);
        req.setJdTitle("Java Developer");
        req.setJdText("Spring Boot backend role with REST APIs.");
        req.setInterviewMode("L2");
        req.setResumeSummary("3 years Java, Spring, PostgreSQL.");
        req.setCandidateProfileJson("{\"level\":\"mid\",\"yearsOfExperience\":3}");
        return req;
    }

    private Map<String, Object> resultWithFeedback() {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> feedback = new LinkedHashMap<>();
        feedback.put("summary", "Good effort");
        result.put("candidateFeedback", feedback);
        return result;
    }
}
