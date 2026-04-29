package com.benchreadiness.ai.controller;

import com.benchreadiness.ai.dto.AssessmentRequest;
import com.benchreadiness.ai.dto.NextQuestionRequest;
import com.benchreadiness.ai.dto.RubricRequest;
import com.benchreadiness.ai.service.AssessmentService;
import com.benchreadiness.ai.service.OpenAiClient;
import com.benchreadiness.ai.service.QuestionService;
import com.benchreadiness.ai.service.RubricService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/ai")
public class AiController {

    private final QuestionService questionService;
    private final AssessmentService assessmentService;
    private final RubricService rubricService;
    private final OpenAiClient openAiClient;

    public AiController(QuestionService questionService, AssessmentService assessmentService, 
                       RubricService rubricService, OpenAiClient openAiClient) {
        this.questionService = questionService;
        this.assessmentService = assessmentService;
        this.rubricService = rubricService;
        this.openAiClient = openAiClient;
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "claudeConfigured", openAiClient.isConfigured()
        ));
    }

    @GetMapping("/test-claude")
    public ResponseEntity<?> testClaude() {
        try {
            if (!openAiClient.isConfigured()) {
                return ResponseEntity.ok(Map.of(
                    "configured", false,
                    "message", "Claude API key not configured"
                ));
            }
            
            String response = openAiClient.chatQuestion(
                "You are a test assistant. Respond with exactly: {\"test\": \"success\"}",
                "Test message"
            );
            
            return ResponseEntity.ok(Map.of(
                "configured", true,
                "testSuccessful", true,
                "response", response
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "configured", true,
                "testSuccessful", false,
                "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/next-question")
    public ResponseEntity<?> nextQuestion(@RequestBody NextQuestionRequest req,
                                         @RequestHeader("X-User-Id") String userId) {
        QuestionService.QuestionResult result = questionService.getNextQuestion(req, userId);
        return ResponseEntity.ok(Map.of(
            "question", result.question(),
            "manipulationDetected", result.manipulationDetected(),
            "terminateInterview", result.terminateInterview()
        ));
    }

    @PostMapping("/assess")
    public ResponseEntity<?> assess(@RequestBody AssessmentRequest req,
                                   @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(assessmentService.assess(req, userId));
    }

    @PostMapping("/debug-assess")
    public ResponseEntity<?> debugAssess(@RequestBody Map<String, Object> request) {
        try {
            // Create a simple test transcript
            String testTranscript = "{\"utterances\": [" +
                "{\"speaker\": \"BOT\", \"text\": \"Tell me about your Java experience\"}," +
                "{\"speaker\": \"CANDIDATE\", \"text\": \"I have 5 years of Java experience working with Spring Boot, microservices, and REST APIs. I've built several backend systems.\"}," +
                "{\"speaker\": \"BOT\", \"text\": \"Can you explain dependency injection?\"}," +
                "{\"speaker\": \"CANDIDATE\", \"text\": \"Dependency injection is a design pattern where objects receive their dependencies from external sources rather than creating them internally. Spring uses IoC container for this.\"}" +
                "]}";
            
            AssessmentRequest req = new AssessmentRequest();
            req.setJdTitle("Senior Java Developer");
            req.setJdText("5+ years Java, Spring Boot, microservices experience required");
            req.setResumeSummary("5 years Java developer with Spring Boot experience");
            req.setTranscriptJson(testTranscript);
            req.setInterviewMode("L2");
            req.setInterviewId("debug-test");
            
            Map<String, Object> result = assessmentService.assess(req, "debug-user");
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "result", result,
                "source", result.get("source")
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage(),
                "errorType", e.getClass().getSimpleName()
            ));
        }
    }

    @PostMapping("/generate-rubric")
    public ResponseEntity<?> generateRubric(@RequestBody RubricRequest req,
                                           @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(rubricService.generateRubric(req, userId));
    }
}
