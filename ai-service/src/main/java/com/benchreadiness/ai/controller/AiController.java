package com.benchreadiness.ai.controller;

import com.benchreadiness.ai.dto.AssessmentRequest;
import com.benchreadiness.ai.dto.MatchingRequest;
import com.benchreadiness.ai.dto.NextQuestionRequest;
import com.benchreadiness.ai.dto.RubricRequest;
import com.benchreadiness.ai.service.AiMatchingService;
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
    private final AiMatchingService aiMatchingService;
    private final OpenAiClient openAiClient;

    public AiController(QuestionService questionService, AssessmentService assessmentService, 
                       RubricService rubricService, AiMatchingService aiMatchingService, OpenAiClient openAiClient) {
        this.questionService = questionService;
        this.assessmentService = assessmentService;
        this.rubricService = rubricService;
        this.aiMatchingService = aiMatchingService;
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

    @PostMapping("/match-candidates")
    public ResponseEntity<?> matchCandidates(@RequestBody MatchingRequest req,
                                           @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(aiMatchingService.matchCandidates(req, userId));
    }

    @PostMapping("/resume-summary")
    public ResponseEntity<?> generateResumeSummary(@RequestBody Map<String, Object> request) {
        try {
            String resumeText = (String) request.get("resumeText");
            String candidateName = (String) request.get("candidateName");
            
            if (resumeText == null || resumeText.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Resume text is required"
                ));
            }
            
            if (candidateName == null || candidateName.trim().isEmpty()) {
                candidateName = "Candidate";
            }
            
            String summary = generateAiResumeSummary(resumeText, candidateName);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "summary", summary,
                "candidateName", candidateName
            ));
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "Failed to generate resume summary: " + e.getMessage()
            ));
        }
    }
    
    private String generateAiResumeSummary(String resumeText, String candidateName) {
        try {
            String prompt = "You are an expert technical recruiter. Analyze this resume and create a concise, professional summary for interview purposes.\n\n" +
                "Guidelines:\n" +
                "- Focus on technical skills, experience level, and key achievements\n" +
                "- Identify primary technology stack and years of experience\n" +
                "- Highlight relevant projects or leadership experience\n" +
                "- Keep it under 150 words\n" +
                "- Be objective and factual\n" +
                "- Format as a single paragraph\n\n" +
                "Candidate Name: " + candidateName + "\n" +
                "Resume Content:\n" + resumeText;
            
            String response = openAiClient.chatQuestion(prompt, "Generate a professional resume summary");
            
            // Clean up the response
            if (response != null) {
                response = response.trim();
                // Remove any JSON formatting if present
                if (response.startsWith("{") && response.endsWith("}")) {
                    // Try to extract summary from JSON
                    try {
                        int summaryStart = response.indexOf("\"summary\":");
                        if (summaryStart != -1) {
                            summaryStart = response.indexOf(":", summaryStart) + 1;
                            int summaryEnd = response.lastIndexOf("\"");
                            if (summaryEnd > summaryStart) {
                                response = response.substring(summaryStart, summaryEnd)
                                    .replace("\"", "")
                                    .trim();
                            }
                        }
                    } catch (Exception e) {
                        // If JSON parsing fails, use the whole response
                    }
                }
            }
            
            return response != null ? response : generateFallbackSummary(resumeText, candidateName);
            
        } catch (Exception e) {
            System.err.println("AI resume summary generation failed: " + e.getMessage());
            return generateFallbackSummary(resumeText, candidateName);
        }
    }
    
    private String generateFallbackSummary(String resumeText, String candidateName) {
        StringBuilder summary = new StringBuilder();
        summary.append(candidateName).append(" - ");
        
        String lowerText = resumeText.toLowerCase();
        
        // Identify experience level
        if (lowerText.contains("senior") || lowerText.contains("lead")) {
            summary.append("Senior level professional");
        } else if (lowerText.contains("junior") || lowerText.contains("associate")) {
            summary.append("Junior level professional");
        } else {
            summary.append("Professional");
        }
        
        // Identify primary skills
        if (lowerText.contains("java") && lowerText.contains("spring")) {
            summary.append(" with Java & Spring Boot expertise");
        } else if (lowerText.contains("react") && lowerText.contains("javascript")) {
            summary.append(" with React & JavaScript expertise");
        } else if (lowerText.contains("python")) {
            summary.append(" with Python expertise");
        } else if (lowerText.contains("java")) {
            summary.append(" with Java expertise");
        }
        
        // Try to extract years of experience
        String[] lines = resumeText.split("\\n");
        for (String line : lines) {
            String lowerLine = line.toLowerCase();
            if (lowerLine.contains("year") && lowerLine.contains("experience")) {
                summary.append(". ").append(line.trim());
                break;
            }
        }
        
        summary.append(". Resume processed and ready for interview creation.");
        
        return summary.toString();
    }
}
