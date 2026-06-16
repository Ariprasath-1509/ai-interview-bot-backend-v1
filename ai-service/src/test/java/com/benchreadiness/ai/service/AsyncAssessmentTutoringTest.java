package com.benchreadiness.ai.service;

import com.benchreadiness.ai.client.ComplianceServiceClient;
import com.benchreadiness.ai.client.InterviewServiceClient;
import com.benchreadiness.ai.dto.AssessmentRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsyncAssessmentTutoringTest {

    @Mock
    private AssessmentService assessmentService;

    @Mock
    private ComplianceServiceClient complianceServiceClient;

    @Mock
    private InterviewServiceClient interviewServiceClient;

    private AsyncAssessmentService asyncAssessmentService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        asyncAssessmentService = new AsyncAssessmentService(
                assessmentService, complianceServiceClient, interviewServiceClient);
    }

    @Test
    void processAssessmentAsync_completedResultIncludesQuestionTutorials() throws Exception {
        String interviewId = "iv-async-1";
        AssessmentRequest req = new AssessmentRequest();
        req.setInterviewId(interviewId);

        Map<String, Object> assessmentResult = buildAssessmentWithTutorials();
        when(assessmentService.assess(eq(req), eq("candidate-1"))).thenReturn(assessmentResult);

        asyncAssessmentService.processAssessmentAsync(req, "candidate-1");

        AsyncAssessmentService.AssessmentStatus status = asyncAssessmentService.getAssessmentStatus(interviewId);
        assertEquals("COMPLETED", status.getStatus());
        assertNotNull(status.getResult());

        Map<String, Object> parsed = objectMapper.readValue(status.getResult(), new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        Map<String, Object> feedback = (Map<String, Object>) parsed.get("candidateFeedback");
        assertNotNull(feedback);
        assertEquals("COMPLETED", feedback.get("tutoringStatus"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tutorials = (List<Map<String, Object>>) feedback.get("questionTutorials");
        assertEquals(2, tutorials.size());
        assertEquals("strong", tutorials.get(0).get("coverage"));

        verify(interviewServiceClient).updateAssessmentStatus(eq(interviewId), argThat(body ->
                "COMPLETED".equals(body.get("status"))
                        && body.get("resultJson") != null
                        && body.get("resultJson").toString().contains("questionTutorials")));
    }

    private Map<String, Object> buildAssessmentWithTutorials() {
        Map<String, Object> tutorial1 = Map.of(
                "slotNumber", 1,
                "questionType", "TECHNICAL",
                "tags", List.of("Java"),
                "question", "Explain immutability.",
                "candidateAnswer", "final fields and unmodifiable collections",
                "expectedAnswer", "Define immutability and give one Java example.",
                "tutorNote", "Mention String immutability.",
                "coverage", "strong"
        );
        Map<String, Object> tutorial2 = Map.of(
                "slotNumber", 2,
                "questionType", "BEHAVIORAL",
                "tags", List.of(),
                "question", "Tell me about a conflict.",
                "candidateAnswer", "We disagreed on approach and aligned in a design review",
                "expectedAnswer", "STAR format with measurable outcome.",
                "tutorNote", "Quantify the impact.",
                "coverage", "partial"
        );

        Map<String, Object> feedback = new LinkedHashMap<>();
        feedback.put("summary", "Solid interview");
        feedback.put("tutoringStatus", "COMPLETED");
        feedback.put("questionTutorials", List.of(tutorial1, tutorial2));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("proposedVerdict", "NEEDS_1_WEEK_PREP");
        result.put("summary", "Manager summary");
        result.put("candidateFeedback", feedback);
        result.put("source", "ollama-four-stage");
        return result;
    }
}
