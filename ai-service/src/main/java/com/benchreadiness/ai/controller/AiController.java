package com.benchreadiness.ai.controller;

import com.benchreadiness.ai.dto.AssessmentRequest;
import com.benchreadiness.ai.dto.DigestAiResponse;
import com.benchreadiness.ai.dto.DigestParseRequest;
import com.benchreadiness.ai.dto.MatchingRequest;
import com.benchreadiness.ai.dto.NextQuestionRequest;
import com.benchreadiness.ai.dto.RubricRequest;
import com.benchreadiness.ai.service.AiMatchingService;
import com.benchreadiness.ai.service.AssessmentService;
import com.benchreadiness.ai.service.AsyncAssessmentService;
import com.benchreadiness.ai.service.DigestParseService;
import com.benchreadiness.ai.service.HybridLlmClient;
import com.benchreadiness.ai.service.LlmClient;
import com.benchreadiness.ai.service.LlmProviderSettings;
import com.benchreadiness.ai.service.QuestionService;
import com.benchreadiness.ai.service.RubricService;
import com.benchreadiness.ai.service.MediaHealthService;
import com.benchreadiness.ai.service.TranscribeService;
import com.benchreadiness.ai.service.TtsService;
import com.benchreadiness.ai.service.JsonRepairUtil;
import jakarta.validation.Valid;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final QuestionService questionService;
    private final AssessmentService assessmentService;
    private final AsyncAssessmentService asyncAssessmentService;
    private final RubricService rubricService;
    private final AiMatchingService aiMatchingService;
    private final LlmClient llmClient;
    private final TranscribeService transcribeService;
    private final TtsService ttsService;
    private final MediaHealthService mediaHealthService;
    private final DigestParseService digestParseService;
    private final ObjectMapper objectMapper;

    public AiController(QuestionService questionService, AssessmentService assessmentService,
                       AsyncAssessmentService asyncAssessmentService, RubricService rubricService,
                       AiMatchingService aiMatchingService, LlmClient llmClient,
                       TranscribeService transcribeService, TtsService ttsService,
                       MediaHealthService mediaHealthService, DigestParseService digestParseService,
                       ObjectMapper objectMapper) {
        this.questionService = questionService;
        this.assessmentService = assessmentService;
        this.asyncAssessmentService = asyncAssessmentService;
        this.rubricService = rubricService;
        this.aiMatchingService = aiMatchingService;
        this.llmClient = llmClient;
        this.transcribeService = transcribeService;
        this.ttsService = ttsService;
        this.mediaHealthService = mediaHealthService;
        this.digestParseService = digestParseService;
        this.objectMapper = objectMapper;
    }

    // ── Admin: LLM provider routing ──────────────────────────────────────────

    @GetMapping("/admin/llm-settings")
    public ResponseEntity<?> getLlmSettings() {
        if (!(llmClient instanceof HybridLlmClient hybrid)) {
            return ResponseEntity.ok(Map.of(
                "mode", "single",
                "message", "HybridLlmClient is not active. Restart with app.llm.provider=hybrid to enable per-operation routing.",
                "claudeConfigured", llmClient.isConfigured(),
                "ollamaConfigured", false
            ));
        }
        LlmProviderSettings s = hybrid.getSettings();
        Map<String, Object> resp = new java.util.LinkedHashMap<>();
        resp.put("mode", "hybrid");
        resp.put("claudeConfigured", hybrid.isClaudeConfigured());
        resp.put("ollamaConfigured", hybrid.isOllamaConfigured());
        resp.putAll(s.toMap());
        return ResponseEntity.ok(resp);
    }

    @PutMapping("/admin/llm-settings")
    public ResponseEntity<?> updateLlmSettings(@RequestBody Map<String, String> body) {
        if (!(llmClient instanceof HybridLlmClient hybrid)) {
            return ResponseEntity.status(400).body(Map.of("error", "HybridLlmClient is not active"));
        }
        try {
            hybrid.getSettings().applyMap(body);
            LlmProviderSettings s = hybrid.getSettings();
            Map<String, Object> resp = new java.util.LinkedHashMap<>();
            resp.put("mode", "hybrid");
            resp.put("claudeConfigured", hybrid.isClaudeConfigured());
            resp.put("ollamaConfigured", hybrid.isOllamaConfigured());
            resp.putAll(s.toMap());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        Map<String, Object> media = mediaHealthService.probe();
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "llmConfigured", llmClient.isConfigured(),
            "sttConfigured", transcribeService.isConfigured(),
            "ttsConfigured", ttsService.isConfigured(),
            "whisperReachable", media.get("whisperReachable"),
            "kokoroReachable", media.get("kokoroReachable"),
            "mediaReady", media.get("mediaReady")
        ));
    }

    @GetMapping("/media-health")
    public ResponseEntity<?> mediaHealth() {
        return ResponseEntity.ok(mediaHealthService.probe());
    }

    @GetMapping("/test-llm")
    public ResponseEntity<?> testLlm() {
        try {
            if (!llmClient.isConfigured()) {
                return ResponseEntity.ok(Map.of(
                    "configured", false,
                    "message", "LLM provider not configured"
                ));
            }
            
            String response = llmClient.chatQuestion(
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
                "error", e.getMessage(),
                "errorType", e.getClass().getSimpleName()
            ));
        }
    }

    @GetMapping("/debug-question")
    public ResponseEntity<?> debugQuestion() {
        try {
            NextQuestionRequest req = new NextQuestionRequest();
            req.setSlot(1);
            req.setJdTitle("Senior Java Developer");
            req.setJdText("5+ years Java, Spring Boot experience required");
            req.setLastAnswer("");
            req.setInterviewMode("L2");
            req.setInterviewId("debug-test");
            
            QuestionService.QuestionResult result = questionService.getNextQuestion(req, "debug-user");
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "question", result.question(),
                "manipulationDetected", result.manipulationDetected(),
                "terminateInterview", result.terminateInterview(),
                "llmConfigured", llmClient.isConfigured()
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage(),
                "errorType", e.getClass().getSimpleName(),
                "llmConfigured", llmClient.isConfigured()
            ));
        }
    }

    @PostMapping("/next-question")
    public ResponseEntity<?> nextQuestion(@RequestBody NextQuestionRequest req,
                                         @RequestHeader("X-User-Id") String userId) {
        QuestionService.QuestionResult result = questionService.getNextQuestion(req, userId);
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("question", result.question());
        body.put("manipulationDetected", result.manipulationDetected());
        body.put("terminateInterview", result.terminateInterview());
        body.put("questionBankId", result.questionBankId() != null ? result.questionBankId() : "");
        body.put("source", result.source() != null ? result.source() : "AI_GENERATED");
        body.put("isCoding", result.isCoding());
        body.put("preferredLanguage", result.preferredLanguage() != null ? result.preferredLanguage() : "python");
        if (result.starterCode() != null) {
            body.put("starterCode", result.starterCode());
        }
        return ResponseEntity.ok(body);
    }

    /**
     * Restates the current interview question in simpler language for the candidate.
     * Does not advance the interview or count as an answer — purely a comprehension aid.
     */
    @PostMapping("/explain-question")
    public ResponseEntity<?> explainQuestion(@RequestBody Map<String, String> body,
                                             @RequestHeader("X-User-Id") String userId) {
        String questionText = body.get("questionText");
        if (questionText == null || questionText.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "questionText is required"));
        }
        String explanation = questionService.explainQuestion(
            questionText,
            body.get("jdTitle"),
            body.get("interviewMode"),
            body.get("interviewId"),
            userId);
        return ResponseEntity.ok(Map.of("explanation", explanation));
    }

    @PostMapping("/assess")
    public ResponseEntity<?> assess(@RequestBody AssessmentRequest req,
                                   @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(assessmentService.assess(req, userId));
    }
    
    @PostMapping("/assess-async")
    public ResponseEntity<?> assessAsync(@RequestBody AssessmentRequest req,
                                        @RequestHeader("X-User-Id") String userId) {
        asyncAssessmentService.clearAssessmentStatus(req.getInterviewId());
        String runId = asyncAssessmentService.prepareAssessmentRun(req.getInterviewId());
        // Must invoke @Async method from the controller so Spring proxy applies (not self-invocation).
        asyncAssessmentService.processAssessmentAsync(req, userId, runId);
        return ResponseEntity.ok(Map.of(
            "status", "ASSESSMENT_PENDING",
            "message", "Assessment queued for processing",
            "interviewId", req.getInterviewId(),
            "runId", runId
        ));
    }

    @PostMapping("/generate-client-brief")
    public ResponseEntity<?> generateClientBrief(@RequestBody Map<String, Object> body,
                                                 @RequestHeader("X-User-Id") String userId) {
        try {
            if (body.get("interviewId") == null || body.get("interviewId").toString().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "interviewId is required"));
            }
            Map<String, Object> brief = assessmentService.generateClientBriefFromAssessment(body, userId);
            return ResponseEntity.ok(brief);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Client brief generation failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to generate client brief"));
        }
    }
    
    @GetMapping("/assess-status/{interviewId}")
    public ResponseEntity<?> getAssessmentStatus(@PathVariable String interviewId,
                                                 @RequestParam(required = false) String runId) {
        AsyncAssessmentService.AssessmentStatus status = asyncAssessmentService.getAssessmentStatus(interviewId);
        String statusRunId = status.getRunId();

        if ("COMPLETED".equals(status.getStatus()) && runId != null && !runId.isBlank()
                && (statusRunId == null || !statusRunId.equals(runId))) {
            return ResponseEntity.ok(Map.of(
                "status", "PROCESSING",
                "message", "A newer assessment run is in progress",
                "runId", statusRunId != null ? statusRunId : ""
            ));
        }

        if ("COMPLETED".equals(status.getStatus())) {
            try {
                Map<String, Object> result = objectMapper.readValue(status.getResult(), 
                    new com.fasterxml.jackson.core.type.TypeReference<>() {});
                return ResponseEntity.ok(Map.of(
                    "status", "COMPLETED",
                    "result", result,
                    "runId", statusRunId != null ? statusRunId : ""
                ));
            } catch (Exception e) {
                return ResponseEntity.ok(Map.of(
                    "status", "FAILED",
                    "error", "Failed to parse assessment result",
                    "runId", statusRunId != null ? statusRunId : ""
                ));
            }
        } else if ("FAILED".equals(status.getStatus())) {
            return ResponseEntity.ok(Map.of(
                "status", "FAILED",
                "error", status.getError(),
                "runId", statusRunId != null ? statusRunId : ""
            ));
        } else if ("PROCESSING".equals(status.getStatus())) {
            return ResponseEntity.ok(Map.of(
                "status", "PROCESSING",
                "message", "Assessment in progress",
                "runId", statusRunId != null ? statusRunId : ""
            ));
        } else {
            return ResponseEntity.ok(Map.of(
                "status", "NOT_FOUND",
                "message", "No assessment found for this interview"
            ));
        }
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

    /**
     * POST /ai/analyze-code — AI review of candidate's code submission.
     * Returns: correctness, timeComplexity, spaceComplexity, bugs, improvements, overallFeedback, score (1-5)
     */
    @PostMapping("/analyze-code")
    public ResponseEntity<?> analyzeCode(@RequestBody Map<String, Object> req,
                                         @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        try {
            String code = (String) req.getOrDefault("code", "");
            String language = (String) req.getOrDefault("language", "python");
            String question = (String) req.getOrDefault("question", "");
            String stdout = (String) req.getOrDefault("stdout", "");
            String stderr = (String) req.getOrDefault("stderr", "");
            boolean passed = Boolean.TRUE.equals(req.get("passed"));

            if (code == null || code.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "code is required"));
            }

            String system =
                "You are a senior software engineer conducting a technical interview code review.\n" +
                "Analyze the candidate's code submission and return ONLY a valid JSON object with this exact schema:\n" +
                "{\n" +
                "  \"correctness\": \"correct|partial|incorrect\",\n" +
                "  \"timeComplexity\": \"e.g. O(n log n)\",\n" +
                "  \"spaceComplexity\": \"e.g. O(n)\",\n" +
                "  \"score\": 1-5,\n" +
                "  \"bugs\": [\"bug description 1\", ...],\n" +
                "  \"improvements\": [\"improvement 1\", ...],\n" +
                "  \"overallFeedback\": \"2-3 sentence summary\"\n" +
                "}\n" +
                "Score rubric: 5=optimal+clean, 4=correct+minor issues, 3=mostly correct, 2=partial, 1=incorrect/incomplete.\n" +
                "No markdown fences. No extra text. Raw JSON only.";

            String user = "Language: " + language + "\n" +
                (question.isBlank() ? "" : "Question: " + question.substring(0, Math.min(500, question.length())) + "\n") +
                "Code:\n" + code.substring(0, Math.min(3000, code.length())) + "\n" +
                (stdout.isBlank() ? "" : "Execution output: " + stdout.substring(0, Math.min(500, stdout.length())) + "\n") +
                (stderr.isBlank() ? "" : "Errors: " + stderr.substring(0, Math.min(300, stderr.length())) + "\n") +
                "Tests passed: " + passed + "\n" +
                "Analyze this code submission.";

            String raw = llmClient.chatQuestion(system, user);
            raw = JsonRepairUtil.repair(raw);
            com.fasterxml.jackson.databind.JsonNode result = objectMapper.readTree(raw);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            // Fallback heuristic if LLM fails
            return ResponseEntity.ok(Map.of(
                "correctness", "unknown",
                "timeComplexity", "unknown",
                "spaceComplexity", "unknown",
                "score", 0,
                "bugs", java.util.List.of(),
                "improvements", java.util.List.of("LLM analysis unavailable: " + e.getMessage()),
                "overallFeedback", "Automated analysis could not be completed. Please review manually."
            ));
        }
    }

    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> transcribe(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam(required = false) String language) {
        if (!transcribeService.isConfigured()) {
            return ResponseEntity.status(503).body(Map.of(
                "error", "stt_not_configured",
                "detail", "Set APP_MEDIA_WHISPER_URL (faster-whisper on port 6013) or Ollama transcribe."
            ));
        }
        if (audio == null || audio.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "audio_required"));
        }
        try {
            return ResponseEntity.ok(transcribeService.transcribe(audio, language));
        } catch (Exception e) {
            log.error("[STT] transcribe failed: {}", e.getMessage(), e);
            return ResponseEntity.status(502).body(Map.of(
                "error", "transcribe_failed",
                "detail", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()
            ));
        }
    }

    @PostMapping("/tts")
    public ResponseEntity<?> tts(@RequestBody Map<String, Object> body) {
        if (!ttsService.isConfigured()) {
            return ResponseEntity.status(503).body(Map.of(
                "error", "tts_not_configured",
                "detail", "Set APP_MEDIA_KOKORO_URL (Kokoro TTS on port 6014)."
            ));
        }
        String text = body.get("text") instanceof String s ? s : "";
        if (text.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "text_required"));
        }
        try {
            byte[] audio = ttsService.synthesize(text);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                    .body(audio);
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of(
                "error", "tts_failed",
                "detail", e.getMessage()
            ));
        }
    }

    /**
     * POST /ai/digest-parse
     * Accepts raw interview text + allowed category list from the questionbank-service,
     * calls the LLM, and returns structured sessions + questions.
     * The questionbank-service then performs fuzzy matching and DB commit separately.
     */
    @PostMapping("/digest-parse")
    public ResponseEntity<?> digestParse(@Valid @RequestBody DigestParseRequest req) {
        try {
            DigestAiResponse result = digestParseService.parse(req.rawText(), req.categoryList());
            return ResponseEntity.ok(Map.of("success", true, "data", result));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("[DigestParse] Failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Digest parse failed: " + e.getMessage()));
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

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody Map<String, Object> request) {
        try {
            String system = (String) request.getOrDefault("system", "You are a helpful assistant.");
            String user = (String) request.getOrDefault("user", "");
            // caller can pass "mode": "structured" for large JSON responses (e.g. problem formatting)
            String mode = (String) request.getOrDefault("mode", "question");

            if (user.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "user message is required"));
            }

            String response = "structured".equals(mode)
                ? llmClient.chatDigest(system, user)    // 8000 tokens — enough for full JSON
                : llmClient.chatQuestion(system, user); // 300 tokens — short answers

            return ResponseEntity.ok(Map.of(
                "success", true,
                "response", response
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
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
            
            String response = llmClient.chatQuestion(prompt, "Generate a professional resume summary");
            
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
            log.error("AI resume summary generation failed: {}", e.getMessage());
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
