package com.benchreadiness.ai.service;

import com.benchreadiness.ai.client.ComplianceServiceClient;
import com.benchreadiness.ai.dto.AssessmentRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class AssessmentService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AssessmentService.class);

    private final LlmClient llmClient;
    private final RubricService rubricService;
    private final ComplianceServiceClient complianceServiceClient;
    private final ClientBriefGenerationService clientBriefGenerationService;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, CachedAssessmentResult> assessmentCache = new ConcurrentHashMap<>();
    private static final long ASSESSMENT_CACHE_TTL_MS = 24 * 60 * 60 * 1000; // 24 hours

    private record CachedAssessmentResult(String payloadJson, long createdAtMs) {}

    public AssessmentService(LlmClient llmClient, RubricService rubricService,
                             ComplianceServiceClient complianceServiceClient,
                             ClientBriefGenerationService clientBriefGenerationService,
                             ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.rubricService = rubricService;
        this.complianceServiceClient = complianceServiceClient;
        this.clientBriefGenerationService = clientBriefGenerationService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> assess(AssessmentRequest req, String userId) {
        String cacheKey = buildAssessmentCacheKey(req);
        if (!req.isForceRefresh()) {
            Map<String, Object> cached = getCachedAssessment(cacheKey);
            if (cached != null) {
                log.info("Returning cached assessment for interview {}", req.getInterviewId());
                return cached;
            }
        } else {
            String interviewId = req.getInterviewId() != null ? req.getInterviewId() : "unknown";
            assessmentCache.keySet().removeIf(key -> key.startsWith(interviewId + "|"));
            log.info("Force refresh — bypassing assessment cache for interview {}", req.getInterviewId());
        }

        List<Map<String, String>> utterances = parseUtterances(req.getTranscriptJson());
        long candidateWords = utterances.stream()
            .filter(u -> "CANDIDATE".equals(u.get("speaker")))
            .filter(u -> !"[SKIPPED]".equals(u.get("text")))
            .mapToLong(u -> u.get("text").split("\\s+").length).sum();
        long candidateTurns = utterances.stream()
            .filter(u -> "CANDIDATE".equals(u.get("speaker")))
            .filter(u -> !"[SKIPPED]".equals(u.get("text"))).count();
            
        log.info("Assessment request for interview {}: {} candidate words, {} candidate turns", 
                req.getInterviewId(), candidateWords, candidateTurns);
            
        // Thin transcript: still assess when substantive code was submitted
        if (candidateWords < 50 || candidateTurns < 3) {
            if (hasSubstantiveCodeSubmissions(req.getCodeSubmissionJson())) {
                log.info("Thin transcript for interview {} ({} words, {} turns) — using coding-only assessment",
                        req.getInterviewId(), candidateWords, candidateTurns);
                Map<String, Object> result = codingOnlyAssessment(req, utterances, candidateWords, candidateTurns);
                result.put("speechAnalytics", computeSpeechAnalytics(utterances));
                cacheAssessment(cacheKey, result);
                return result;
            }
            log.warn("Insufficient responses for interview {}: {} words, {} turns",
                    req.getInterviewId(), candidateWords, candidateTurns);
            Map<String, Object> result = thinTranscriptResult(
                    "Insufficient responses — candidate answered fewer than 3 questions or provided less than 50 words total.");
            result.put("speechAnalytics", computeSpeechAnalytics(utterances));
            cacheAssessment(cacheKey, result);
            return result;
        }

        boolean substantialTranscript = candidateWords >= 200 && candidateTurns >= 3;
        if (!req.isForceRefresh() && !substantialTranscript) {
            double transcriptionQuality = assessTranscriptionQuality(utterances);
            log.info("Transcription quality score for interview {}: {}", req.getInterviewId(), transcriptionQuality);

            if (transcriptionQuality < 0.5) {
                log.warn("Poor transcription quality detected for interview {} (score: {}). Assessment may be unreliable.",
                        req.getInterviewId(), transcriptionQuality);
                Map<String, Object> result = poorTranscriptionResult(
                    "Poor audio/transcription quality detected. Please review the transcript manually before making hiring decisions.",
                    transcriptionQuality
                );
                cacheAssessment(cacheKey, result);
                return result;
            }
        } else if (req.isForceRefresh()) {
            log.info("Force refresh — running full AI assessment regardless of transcription quality heuristics for interview {}",
                    req.getInterviewId());
        } else {
            log.info("Substantial transcript ({} words, {} turns) — proceeding to AI assessment for interview {}",
                    candidateWords, candidateTurns, req.getInterviewId());
        }
        
        if (!llmClient.isConfigured()) {
            log.warn("LLM provider not configured - falling back to heuristic assessment");
            Map<String, Object> result = heuristicAssessment(utterances);
            result.put("speechAnalytics", computeSpeechAnalytics(utterances));
            cacheAssessment(cacheKey, result);
            return result;
        }
        try {
            log.info("Starting two-pass LLM assessment for interview: {}", req.getInterviewId());
            Map<String, Object> result = twoPassAssessment(req, utterances, userId);
            // Inject speech analytics computed from transcript
            result.put("speechAnalytics", computeSpeechAnalytics(utterances));
            cacheAssessment(cacheKey, result);
            return result;
        } catch (Exception e) {
            log.error("LLM assessment failed for interview {}: {}", req.getInterviewId(), e.getMessage(), e);
            Map<String, Object> result = heuristicAssessment(utterances);
            cacheAssessment(cacheKey, result);
            return result;
        }
    }

    private Map<String, Object> computeSpeechAnalytics(List<Map<String, String>> utterances) {
        List<String> candidateLines = utterances.stream()
            .filter(u -> "CANDIDATE".equals(u.get("speaker")))
            .map(u -> u.getOrDefault("text", "").trim())
            .filter(t -> !t.isEmpty() && !"[SKIPPED]".equals(t))
            .toList();

        String fullText = String.join(" ", candidateLines);
        String[] words = fullText.isBlank() ? new String[0] : fullText.split("\\s+");

        // Filler word detection
        java.util.regex.Pattern fillerPattern = java.util.regex.Pattern.compile(
            "\\b(um|uh|like|you know|sort of|kind of|basically|literally|actually|right|okay|so)\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        int fillers = 0;
        java.util.regex.Matcher m = fillerPattern.matcher(fullText);
        while (m.find()) fillers++;

        // Duration estimate from timestamps
        long durationMs = 0;
        try {
            List<Long> timestamps = utterances.stream()
                .map(u -> u.getOrDefault("at", ""))
                .filter(t -> !t.isEmpty())
                .map(t -> java.time.Instant.parse(t).toEpochMilli())
                .toList();
            if (timestamps.size() >= 2) {
                durationMs = timestamps.stream().mapToLong(Long::longValue).max().orElse(0)
                           - timestamps.stream().mapToLong(Long::longValue).min().orElse(0);
            }
        } catch (Exception ignored) {}

        double durationMin = durationMs > 0 ? durationMs / 60000.0 : Math.max(1, candidateLines.size() * 1.5);
        int wpm = words.length > 0 ? (int) Math.round(words.length / durationMin) : 0;

        // Long silences: gaps between consecutive candidate turns > 30s
        int longSilences = 0;
        try {
            List<Long> candidateTimestamps = utterances.stream()
                .filter(u -> "CANDIDATE".equals(u.get("speaker")))
                .map(u -> u.getOrDefault("at", ""))
                .filter(t -> !t.isEmpty())
                .map(t -> java.time.Instant.parse(t).toEpochMilli())
                .sorted()
                .toList();
            for (int i = 1; i < candidateTimestamps.size(); i++) {
                if (candidateTimestamps.get(i) - candidateTimestamps.get(i - 1) > 30_000) longSilences++;
            }
        } catch (Exception ignored) {}

        Map<String, Object> analytics = new java.util.LinkedHashMap<>();
        analytics.put("wpm", wpm);
        analytics.put("fillers", fillers);
        analytics.put("longSilences", longSilences);
        analytics.put("wordCount", words.length);
        analytics.put("candidateTurns", candidateLines.size());
        return analytics;
    }

    private String buildAssessmentCacheKey(AssessmentRequest req) {
        String interviewId = req.getInterviewId() != null ? req.getInterviewId() : "unknown";
        String material = String.join("|",
            req.getTranscriptJson() != null ? req.getTranscriptJson() : "",
            req.getRubricJson() != null ? req.getRubricJson() : "",
            req.getCandidateProfileJson() != null ? req.getCandidateProfileJson() : "",
            req.getJdTitle() != null ? req.getJdTitle() : "",
            req.getInterviewMode() != null ? req.getInterviewMode() : "",
            req.getCodeSubmissionJson() != null ? req.getCodeSubmissionJson() : ""
        );
        return interviewId + "|" + sha256(material);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private Map<String, Object> getCachedAssessment(String key) {
        CachedAssessmentResult cached = assessmentCache.get(key);
        if (cached == null) return null;
        long age = System.currentTimeMillis() - cached.createdAtMs();
        if (age > ASSESSMENT_CACHE_TTL_MS) {
            assessmentCache.remove(key);
            return null;
        }
        try {
            return objectMapper.readValue(cached.payloadJson(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            assessmentCache.remove(key);
            return null;
        }
    }

    private void cacheAssessment(String key, Map<String, Object> result) {
        try {
            String payload = objectMapper.writeValueAsString(result);
            assessmentCache.put(key, new CachedAssessmentResult(payload, System.currentTimeMillis()));
            if (assessmentCache.size() > 2000) {
                clearExpiredAssessmentCache();
            }
        } catch (Exception e) {
            log.warn("Failed to cache assessment result: {}", e.getMessage());
        }
    }

    private void clearExpiredAssessmentCache() {
        long now = System.currentTimeMillis();
        assessmentCache.entrySet().removeIf(entry -> (now - entry.getValue().createdAtMs()) > ASSESSMENT_CACHE_TTL_MS);
    }

    // ── Pass 1: Evidence extraction ──────────────────────────────────────────
    private Map<String, List<String>> extractEvidence(
            List<Map<String, String>> utterances, List<Map<String, Object>> categories,
            String interviewId, String userId, String codeSubmissionJson) throws Exception {

        String categoryList = categories.stream()
            .map(c -> "- " + c.get("key") + ": " + c.get("description"))
            .reduce("", (a, b) -> a + "\n" + b);

        String system =
            "Act as a literal text parser. Extract concrete technical evidence from the candidate interview transcript for each provided category definition.\n" +
            "\n" +
            "TARGET CATEGORIES LOG:\n" + categoryList + "\n" +
            "\n" +
            "EXECUTION CONSTRAINTS:\n" +
            "1. Each item within the category array MUST be a direct quote or a tight paraphrase capped at a maximum of 25 words.\n" +
            "2. Do NOT infer, extrapolate, or assume technical competence. If the transcript contains zero matching validation for a specific category key, return an empty array `[]`.\n" +
            "3. Output a single raw JSON block. No introductory prose, no markdown formatting.\n" +
            "{\"categoryKey\": [\"extracted evidence quote 1\", \"extracted evidence quote 2\"]}";

        String transcript = buildEfficientTranscript(utterances);
        String codeContext = buildCodeSubmissionContext(codeSubmissionJson);
        String userContent = "Transcript:\n" + transcript;
        if (!codeContext.isBlank()) {
            userContent += "\n\nCode submissions (include in evidence where relevant):\n" + codeContext;
        }
        String raw = llmClient.chatAssessmentWithTracking(system, userContent, interviewId, userId);
        JsonNode json = objectMapper.readTree(JsonRepairUtil.repair(raw));

        Map<String, List<String>> evidence = new LinkedHashMap<>();
        categories.forEach(c -> {
            String key = (String) c.get("key");
            List<String> items = new ArrayList<>();
            JsonNode arr = json.path(key);
            if (arr.isArray()) arr.forEach(n -> items.add(n.asText()));
            evidence.put(key, items);
        });
        return evidence;
    }

    // ── Pass 2: Full assessment ───────────────────────────────────────────────
    private Map<String, Object> twoPassAssessment(
            AssessmentRequest req, List<Map<String, String>> utterances, String userId) throws Exception {

        log.info("Starting two-pass assessment for interview: {}", req.getInterviewId());

        // Java-side injection pre-filter — scan full transcript before any LLM call
        int injectionCount = InjectionGuard.countTranscriptInjections(utterances);
        if (injectionCount >= InjectionGuard.TERMINATE_THRESHOLD) {
            log.warn("Transcript injection pre-filter triggered for interview {} ({} injections detected)",
                    req.getInterviewId(), injectionCount);
            Map<String, Object> result = withdrawnResult(
                "Interview terminated: " + injectionCount + " prompt injection attempts detected in transcript.");
            storeAssessmentResponse(req.getInterviewId(),
                    objectMapper.writeValueAsString(result), 0, "injection-terminated", userId);
            return result;
        }

        List<Map<String, Object>> categories;
        if (req.getRubricJson() == null || req.getRubricJson().isBlank()) {
            log.warn("rubricJson not provided for interview {} — this should have been generated at creation time", req.getInterviewId());
            try {
                com.benchreadiness.ai.dto.RubricRequest rubricReq = new com.benchreadiness.ai.dto.RubricRequest();
                rubricReq.setJdTitle(req.getJdTitle());
                rubricReq.setJdText(req.getJdText());
                rubricReq.setResumeSummary(req.getResumeSummary() != null ? req.getResumeSummary() : "");
                rubricReq.setInterviewId(req.getInterviewId());
                Map<String, Object> generated = rubricService.generateRubric(rubricReq, userId);
                categories = parseCategories(objectMapper.writeValueAsString(generated.get("rubric")));
                log.info("Generated {} categories from JD (fallback)", categories.size());
            } catch (Exception e) {
                log.warn("On-the-fly rubric generation failed: {}", e.getMessage());
                categories = defaultCategories();
            }
        } else {
            categories = parseCategories(req.getRubricJson());
            log.info("Using stored rubric with {} categories (no Claude call needed)", categories.size());
        }

        Map<String, Object> candidateProfile = parseCandidateProfile(req.getCandidateProfileJson());
        log.info("Candidate profile: level={}, yoe={}", 
                candidateProfile.getOrDefault("level", "unknown"), 
                candidateProfile.getOrDefault("yearsOfExperience", "unknown"));
        
        log.info("Starting evidence extraction pass...");
        Map<String, List<String>> evidence;
        try {
            evidence = extractEvidence(utterances, categories, req.getInterviewId(), userId, req.getCodeSubmissionJson());
            log.info("Evidence extraction completed for {} categories", evidence.size());
        } catch (Exception e) {
            log.error("Evidence extraction failed: {}", e.getMessage(), e);
            throw e;
        }

        String evidenceSummary = evidence.entrySet().stream()
            .map(e -> e.getKey() + ": " + (e.getValue().isEmpty() ? "no evidence" : String.join("; ", e.getValue())))
            .reduce("", (a, b) -> a + "\n" + b);

        String codeContext = buildCodeSubmissionContext(req.getCodeSubmissionJson());
        if (!codeContext.isBlank()) {
            evidenceSummary = evidenceSummary + "\n\nCode submissions:\n" + codeContext;
        }

        String level = (String) candidateProfile.getOrDefault("level", "mid");
        String yoe = String.valueOf(candidateProfile.getOrDefault("yearsOfExperience", "unknown"));
        Object claimedRaw = candidateProfile.get("claimedExpertise");
        String claimed = claimedRaw != null ? claimedRaw.toString() : "[]";
        String jdSnippet = req.getJdText().substring(0, Math.min(400, req.getJdText().length()));
        String resumeSnippet = req.getResumeSummary() != null
            ? req.getResumeSummary().substring(0, Math.min(400, req.getResumeSummary().length())) : "";

        // ── Stage 1: Category scoring (small, deterministic JSON) ─────────────
        log.info("Stage 1: category scoring for interview {}", req.getInterviewId());
        JsonNode scoresJson = runStage1Scoring(categories, evidenceSummary, level, yoe, req);

        // ── Stage 2: Behavioral signals + resume consistency ──────────────────
        log.info("Stage 2: behavioral + resume consistency for interview {}", req.getInterviewId());
        JsonNode behavioralJson = runStage2Behavioral(evidenceSummary, resumeSnippet, claimed, jdSnippet, req);

        // ── Stage 3: Candidate feedback (pros/cons + roadmap) ─────────────────
        log.info("Stage 3: candidate feedback for interview {}", req.getInterviewId());
        JsonNode feedbackJson = runStage3Feedback(scoresJson, categories, jdSnippet, req);

        // Client brief (Stage 3b) is generated on demand when staff prepares client feedback — not during assess.

        // ── Stage 4: Java aggregator — merge all stages, no LLM call ──────────
        log.info("Stage 4: aggregating results for interview {}", req.getInterviewId());
        Map<String, Object> result = aggregateStages(scoresJson, behavioralJson, feedbackJson, null,
            categories, evidence, req, userId);
        applyReadinessGates(result, categories, evidence, req.getInterviewMode());

        try {
            String assessmentJson = objectMapper.writeValueAsString(result);
            storeAssessmentResponse(req.getInterviewId(), assessmentJson, 0, "ollama-four-stage", userId);
            finalizeInterviewTokens(req.getInterviewId(), userId);
        } catch (Exception e) {
            log.warn("Failed to store assessment response for interview {}: {}", req.getInterviewId(), e.getMessage());
        }

        return result;
    }

    // ── Stage 1: category scores + verdict + summary ──────────────────────────
    private JsonNode runStage1Scoring(List<Map<String, Object>> categories, String evidenceSummary,
                                      String level, String yoe, AssessmentRequest req) throws Exception {
        String categoryScoreSchema = categories.stream()
            .map(c -> "  \"" + c.get("key") + "\": {\"score\": 1-10 or null, \"strengths\": [\"...\"], \"weaknesses\": [\"...\"], \"evidence\": \"direct quote\", \"gap\": \"missing topics\", \"confidence\": \"low|medium|high\"}")
            .collect(java.util.stream.Collectors.joining(",\n"));

        String system =
            "You are an expert technical hiring assessor generating an audit-ready evaluation report.\n" +
            "Profile Target: " + level + " level candidate with " + yoe + " years of professional experience.\n" +
            "\n" +
            "SCORING CALCULATOR MATRIX (1-10 scale):\n" +
            "- 10 = Expert: Proficiently explains distributed scale, trade-offs, and internals.\n" +
            "- 8-9 = Solid: Strong practical implementation and structural knowledge.\n" +
            "- 6-7 = Good: Working knowledge with minor gaps.\n" +
            "- 4-5 = Surface-Level: Basic understanding but lacks deep production knowledge.\n" +
            "- 2-3 = Partial: Knows keywords but fails simple implementation checks.\n" +
            "- 1 = None: Completely incorrect answer or explicitly stated lack of knowledge.\n" +
            "- null = Category was never discussed during the conversation loop.\n" +
            "\n" +
            "VERDICT LOGIC MATRICES FOR ACTIVE MODE:\n" +
            getVerdictRulesForMode(req.getInterviewMode()) + "\n" +
            "\n" +
            "Output ONLY raw JSON. No markdown. No prose.\n" +
            "{\n" +
            "  \"categoryScores\": {\n" + categoryScoreSchema + "\n  },\n" +
            "  \"communication\": {\"score\": 1-10, \"rationale\": \"Detailed explanation of structure and clarity\"},\n" +
            "  \"proposedVerdict\": \"READY|NEEDS_1_WEEK_PREP|NEEDS_RESKILLING|MISMATCH_WITH_JD\",\n" +
            "  \"summary\": \"2-3 sentence overview for an engineering manager\"\n" +
            "}";

        String user = "Role: " + req.getJdTitle() + "\nEvidence per category:\n" + evidenceSummary;
        String raw = llmClient.chatAssessmentWithTracking(system, user, req.getInterviewId(), "stage1");
        log.info("Stage 1 response length: {}", raw.length());
        return objectMapper.readTree(JsonRepairUtil.repair(raw));
    }

    // ── Stage 2: behavioral signals + resume consistency ──────────────────────
    private JsonNode runStage2Behavioral(String evidenceSummary, String resumeSnippet,
                                         String claimed, String jdSnippet, AssessmentRequest req) throws Exception {
        String system =
            "You are a behavioral interview analyst. Analyze the evidence and return ONLY raw JSON. No markdown.\n" +
            "{\n" +
            "  \"behavioralSignals\": {\n" +
            "    \"ownershipLevel\": \"low|medium|high\",\n" +
            "    \"learningAgility\": \"low|medium|high\",\n" +
            "    \"communicationStructure\": \"low|medium|high\",\n" +
            "    \"confidenceCalibration\": \"low|medium|high\",\n" +
            "    \"summary\": \"2 sentence behavioral overview\"\n" +
            "  },\n" +
            "  \"resumeConsistency\": {\n" +
            "    \"claimed\": [\"skill1\"],\n" +
            "    \"demonstrated\": [\"skill1\"],\n" +
            "    \"notDemonstrated\": [\"skill2\"],\n" +
            "    \"consistencyScore\": 1-10,\n" +
            "    \"flags\": [\"flag1\"]\n" +
            "  },\n" +
            "  \"interviewQuality\": {\n" +
            "    \"coverageScore\": 1-10,\n" +
            "    \"categoriesCovered\": [],\n" +
            "    \"categoriesMissed\": [],\n" +
            "    \"note\": \"coverage summary\"\n" +
            "  }\n" +
            "}";

        String user = "Role: " + req.getJdTitle() + "\n" +
            "JD: " + jdSnippet + "\n" +
            "Resume claimed: " + claimed + "\n" +
            "Resume summary: " + resumeSnippet + "\n" +
            "Evidence: " + evidenceSummary;
        String raw = llmClient.chatAssessmentWithTracking(system, user, req.getInterviewId(), "stage2");
        log.info("Stage 2 response length: {}", raw.length());
        return objectMapper.readTree(JsonRepairUtil.repair(raw));
    }

    // ── Stage 3: pros/cons + roadmap + resume consistency for candidate ────────
    private JsonNode runStage3Feedback(JsonNode scoresJson, List<Map<String, Object>> categories,
                                       String jdSnippet, AssessmentRequest req) throws Exception {
        StringBuilder scoresInfo = new StringBuilder();
        JsonNode catScores = scoresJson.path("categoryScores");
        for (Map<String, Object> cat : categories) {
            String key = (String) cat.get("key");
            JsonNode s = catScores.path(key);
            if (!s.isMissingNode()) {
                scoresInfo.append(key).append(": ").append(s.path("score").asText("null"))
                    .append("/10 gap: ").append(s.path("gap").asText("none")).append("\n");
            }
        }

        String system =
            "You are generating candidate feedback for a completed technical interview.\n" +
            "Return ONLY raw JSON. No markdown. No prose.\n" +
            "{\n" +
            "  \"summary\": \"2-3 sentences of constructive feedback directly addressed to the candidate\",\n" +
            "  \"prosAndCons\": [{\"category\": \"Category Key\", \"pros\": [\"Pro 1\"], \"cons\": [\"Con 1\"]}],\n" +
            "  \"resumeConsistencyForCandidate\": [{\"claim\": \"Skill Name\", \"demonstrated\": true, \"note\": \"Verification explanation\"}],\n" +
            "  \"roadmap\": [{\"day\": 1, \"category\": \"Category Key\", \"gap\": \"Identified gap\", \"focus\": \"Target framework topic\", \"whyItMatters\": \"Architectural dependency rationale\", \"resource\": \"Official Documentation Name\", \"resourceUrl\": \"https://documentation-link.org\", \"exercise\": \"Practical coding exercise requirement\", \"estimatedHours\": 2}],\n" +
            "  \"estimatedReadiness\": \"Explicit preparation timeline estimate string\"\n" +
            "}\n" +
            "RULES:\n" +
            "- prosAndCons MUST include exactly ONE entry per scored category.\n" +
            "- roadmap MUST cover days 1-7 for any category score below 8. Use verified open-source documentation URLs.\n" +
            "- resumeConsistencyForCandidate: list 3-5 key JD skills and whether demonstrated.";

        String user = "Role: " + req.getJdTitle() + "\nJD: " + jdSnippet + "\nScores:\n" + scoresInfo;
        String raw = llmClient.chatAssessmentWithTracking(system, user, req.getInterviewId(), "stage3");
        log.info("Stage 3 response length: {}", raw.length());
        return objectMapper.readTree(JsonRepairUtil.repair(raw));
    }

    /**
     * Generate a structured client-facing evaluation brief from a completed assessment (on-demand, staff-only).
     */
    public Map<String, Object> generateClientBriefFromAssessment(Map<String, Object> request, String userId)
            throws Exception {
        return clientBriefGenerationService.generateStructuredBrief(request, userId);
    }

    private static String stringOrEmpty(Object value) {
        return value != null ? value.toString() : "";
    }
    private Map<String, Object> aggregateStages(JsonNode scoresJson, JsonNode behavioralJson,
                                                 JsonNode feedbackJson, JsonNode clientBriefJson,
                                                 List<Map<String, Object>> categories,
                                                 Map<String, List<String>> evidence,
                                                 AssessmentRequest req, String userId) {
        Map<String, Object> result = new LinkedHashMap<>();

        // Category scores from Stage 1
        List<Map<String, Object>> scoreRows = new ArrayList<>();
        JsonNode catScores = scoresJson.path("categoryScores");
        for (Map<String, Object> cat : categories) {
            String key = (String) cat.get("key");
            JsonNode s = catScores.path(key);
            if (!s.path("score").isNull() && !s.path("score").isMissingNode()) {
                scoreRows.add(Map.of(
                    "dimension", key,
                    "value", s.path("score").asInt(),
                    "rationale", s.path("evidence").asText(""),
                    "evidence", String.join("; ", evidence.getOrDefault(key, List.of())),
                    "gap", s.path("gap").asText(""),
                    "strengths", toStringList(s.path("strengths")).toString(),
                    "weaknesses", toStringList(s.path("weaknesses")).toString(),
                    "confidence", s.path("confidence").asText("medium")
                ));
            }
        }
        JsonNode comm = scoresJson.path("communication");
        scoreRows.add(Map.of(
            "dimension", "communication",
            "value", comm.path("score").asInt(6),
            "rationale", comm.path("rationale").asText(""),
            "evidence", "", "gap", "",
            "strengths", "[]", "weaknesses", "[]", "confidence", "medium"
        ));
        result.put("categoryScores", scoreRows);
        result.put("scoreMax", 10);
        result.put("proposedVerdict", scoresJson.path("proposedVerdict").asText("NEEDS_1_WEEK_PREP"));
        result.put("summary", scoresJson.path("summary").asText(""));

        // Behavioral + resume consistency from Stage 2
        JsonNode behavioralSignals = behavioralJson.path("behavioralSignals");
        result.put("behavioralSignals", behavioralSignals.isMissingNode()
            ? Map.of("ownershipLevel", "medium", "learningAgility", "medium",
                     "communicationStructure", "medium", "confidenceCalibration", "medium", "summary", "")
            : parseObject(behavioralSignals));

        JsonNode resumeConsistency = behavioralJson.path("resumeConsistency");
        result.put("resumeConsistency", resumeConsistency.isMissingNode()
            ? Map.of("claimed", List.of(), "demonstrated", List.of(),
                     "notDemonstrated", List.of(), "consistencyScore", 6, "flags", List.of())
            : parseObject(resumeConsistency));

        JsonNode interviewQuality = behavioralJson.path("interviewQuality");
        result.put("interviewQuality", interviewQuality.isMissingNode()
            ? Map.of("coverageScore", 6, "categoriesCovered",
                     categories.stream().map(c -> c.get("key")).toList(),
                     "categoriesMissed", List.of(), "note", "Standard coverage")
            : parseObject(interviewQuality));

        // Candidate feedback from Stage 3
        Map<String, Object> feedback = parseCandidateFeedback(feedbackJson);
        List<?> roadmap = (List<?>) feedback.get("roadmap");
        List<?> prosAndCons = (List<?>) feedback.get("prosAndCons");
        if ((roadmap == null || roadmap.isEmpty()) && (prosAndCons == null || prosAndCons.isEmpty())) {
            log.warn("Stage 3 feedback empty for interview {} — using fallback", req.getInterviewId());
            feedback = generateCandidateFeedbackSeparately(scoreRows, categories, evidence,
                result.get("summary").toString(), req, userId);
        }
        result.put("candidateFeedback", feedback);

        if (clientBriefJson != null && !clientBriefJson.isMissingNode()) {
            // Legacy path — structured brief is generated on demand via ClientBriefGenerationService.
            result.put("clientBrief", objectMapper.convertValue(clientBriefJson, Map.class));
        }
        result.put("source", "ollama-four-stage");
        return result;
    }

    @SuppressWarnings("unchecked")
    private void applyReadinessGates(
            Map<String, Object> result,
            List<Map<String, Object>> categories,
            Map<String, List<String>> evidence,
            String interviewMode
    ) {
        List<Map<String, Object>> scoreRows = (List<Map<String, Object>>) result.getOrDefault("categoryScores", List.of());
        Map<String, Integer> scoreByDimension = new LinkedHashMap<>();
        for (Map<String, Object> row : scoreRows) {
            Object dim = row.get("dimension");
            Object value = row.get("value");
            if (dim == null || value == null) continue;
            try {
                scoreByDimension.put(String.valueOf(dim), Integer.parseInt(String.valueOf(value)));
            } catch (Exception ignored) {
                // ignore malformed score row
            }
        }

        List<String> mustCover = categories.stream()
            .map(c -> String.valueOf(c.getOrDefault("key", "")))
            .filter(k -> !k.isBlank())
            .toList();

        List<String> uncovered = mustCover.stream()
            .filter(key -> evidence.getOrDefault(key, List.of()).isEmpty())
            .toList();

        int minScoreFloor = minCoreScoreFloorForMode(interviewMode);
        List<String> weakDimensions = new ArrayList<>();
        for (String key : mustCover) {
            Integer score = scoreByDimension.get(key);
            if (score != null && score < minScoreFloor) {
                weakDimensions.add(key);
            }
        }
        Integer communication = scoreByDimension.get("communication");
        boolean commWeak = communication != null && communication < Math.max(4, minScoreFloor - 2);

        boolean gatePass = uncovered.isEmpty() && weakDimensions.isEmpty() && !commWeak;
        String originalVerdict = String.valueOf(result.getOrDefault("proposedVerdict", "NEEDS_1_WEEK_PREP"));
        String finalVerdict = originalVerdict;

        if (!gatePass) {
            finalVerdict = weakDimensions.isEmpty() ? "NEEDS_1_WEEK_PREP" : "NEEDS_RESKILLING";
            result.put("proposedVerdict", finalVerdict);
        }

        Map<String, Object> gate = new LinkedHashMap<>();
        gate.put("gatePassed", gatePass);
        gate.put("mode", interviewMode != null ? interviewMode : "L3");
        gate.put("minCoreScoreFloor", minScoreFloor);
        gate.put("mustCoverCategories", mustCover);
        gate.put("uncoveredCategories", uncovered);
        gate.put("weakDimensions", weakDimensions);
        gate.put("communicationWeak", commWeak);
        gate.put("originalVerdict", originalVerdict);
        gate.put("finalVerdict", finalVerdict);
        gate.put("note", gatePass
            ? "Candidate cleared deterministic readiness gates."
            : "Readiness downgraded by deterministic gates due to missing coverage or weak core dimensions.");
        result.put("readinessGate", gate);
    }

    private int minCoreScoreFloorForMode(String mode) {
        String m = mode != null ? mode : "L3";
        return switch (m) {
            case "SCREENING" -> 4;
            case "L1" -> 6;
            case "L2" -> 6;
            case "L3" -> 8;
            case "L4" -> 8;
            default -> 6;
        };
    }

    private String buildEfficientTranscript(List<Map<String, String>> utterances) {
        List<Map<String, String>> candidates = utterances.stream()
            .filter(u -> "CANDIDATE".equals(u.get("speaker"))).toList();

        // Deduplicate consecutive similar answers (skip markers are kept as-is)
        List<Map<String, String>> deduplicated = deduplicateUtterances(candidates);

        int start = Math.max(0, deduplicated.size() - 8);
        StringBuilder sb = new StringBuilder();
        for (Map<String, String> ans : deduplicated.subList(start, deduplicated.size())) {
            int idx = utterances.indexOf(ans);
            if (idx > 0 && "BOT".equals(utterances.get(idx - 1).get("speaker")))
                sb.append("Q: ").append(utterances.get(idx - 1).get("text")).append("\n");
            String text = ans.get("text");
            if ("[SKIPPED]".equals(text)) {
                sb.append("A: [Skipped by candidate — no answer provided]\n\n");
            } else {
                sb.append("A: ").append(text, 0, Math.min(600, text.length())).append("\n\n");
            }
        }
        return sb.toString().trim();
    }

    private Map<String, Object> parseCandidateFeedback(JsonNode node) {
        if (node.isMissingNode()) return Map.of();

        List<Map<String, Object>> prosAndCons = new ArrayList<>();
        node.path("prosAndCons").forEach(item -> prosAndCons.add(Map.of(
            "category", item.path("category").asText(""),
            "pros", toStringList(item.path("pros")),
            "cons", toStringList(item.path("cons"))
        )));

        List<Map<String, Object>> resumeConsistency = new ArrayList<>();
        node.path("resumeConsistencyForCandidate").forEach(item -> {
            boolean demonstrated = item.path("demonstrated").asBoolean(item.path("consistent").asBoolean(false));
            String note = item.path("note").asText(item.path("evidence").asText(""));
            resumeConsistency.add(Map.of(
                "claim", item.path("claim").asText(""),
                "demonstrated", demonstrated,
                "consistent", demonstrated,
                "note", note,
                "evidence", note
            ));
        });

        List<Map<String, Object>> roadmap = new ArrayList<>();
        node.path("roadmap").forEach(day -> {
            int dayNum = day.path("day").asInt(0);
            if (dayNum <= 0) {
                String rawDay = day.path("day").asText("");
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(rawDay);
                if (m.find()) {
                    try {
                        dayNum = Integer.parseInt(m.group(1));
                    } catch (Exception ignored) {
                        dayNum = 0;
                    }
                }
            }
            Map<String, Object> roadmapEntry = new LinkedHashMap<>();
            roadmapEntry.put("day", dayNum > 0 ? dayNum : 1);
            roadmapEntry.put("category", day.path("category").asText(""));
            roadmapEntry.put("gap", day.path("gap").asText(""));
            roadmapEntry.put("focus", day.path("focus").asText(""));
            roadmapEntry.put("whyItMatters", day.path("whyItMatters").asText(""));
            roadmapEntry.put("resource", day.path("resource").asText(""));
            roadmapEntry.put("resourceUrl", day.path("resourceUrl").asText(""));
            roadmapEntry.put("exercise", day.path("exercise").asText(""));
            roadmapEntry.put("estimatedHours", day.path("estimatedHours").asInt(2));
            roadmap.add(roadmapEntry);
        });

        String summary = node.path("summary").asText(node.path("overallSummary").asText(""));
        String estimatedReadiness = node.path("estimatedReadiness").asText(
            node.path("estimatedReadinessTimeline").asText("")
        );

        List<String> strengths = new ArrayList<>();
        List<String> areasToImprove = new ArrayList<>();
        for (Map<String, Object> pc : prosAndCons) {
            Object pros = pc.get("pros");
            Object cons = pc.get("cons");
            if (pros instanceof List<?> p) p.forEach(v -> strengths.add(String.valueOf(v)));
            if (cons instanceof List<?> c) c.forEach(v -> areasToImprove.add(String.valueOf(v)));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", summary);
        out.put("overallSummary", summary);
        out.put("prosAndCons", prosAndCons);
        out.put("strengths", strengths);
        out.put("areasToImprove", areasToImprove);
        out.put("resumeConsistencyForCandidate", resumeConsistency);
        out.put("roadmap", roadmap);
        out.put("estimatedReadiness", estimatedReadiness);
        out.put("estimatedReadinessTimeline", estimatedReadiness);
        return out;
    }

    private Map<String, Object> generateCandidateFeedbackSeparately(
            List<Map<String, Object>> scoreRows, List<Map<String, Object>> categories,
            Map<String, List<String>> evidence, String summary, AssessmentRequest req, String userId) {
        try {
            StringBuilder scoresInfo = new StringBuilder();
            for (Map<String, Object> row : scoreRows) {
                scoresInfo.append(row.get("dimension")).append(": ").append(row.get("value")).append("/10")
                    .append(" gap: ").append(row.getOrDefault("gap", "none")).append("\n");
            }

            String system = "You are generating candidate feedback for a completed technical interview.\n" +
                "Return ONLY valid JSON with this exact structure:\n" +
                "{\n" +
                "  \"summary\": \"2-3 sentences plain English feedback for the candidate\",\n" +
                "  \"prosAndCons\": [\n" +
                "    {\"category\": \"category name\", \"pros\": [\"what they did well\"], \"cons\": [\"what to improve\"]}\n" +
                "  ],\n" +
                "  \"resumeConsistencyForCandidate\": [\n" +
                "    {\"claim\": \"skill from JD/resume\", \"demonstrated\": true/false, \"note\": \"brief explanation\"}\n" +
                "  ],\n" +
                "  \"roadmap\": [\n" +
                "    {\"day\": 1, \"category\": \"name\", \"gap\": \"specific gap\", \"focus\": \"topic\", \"whyItMatters\": \"reason\", \"resource\": \"name\", \"resourceUrl\": \"url\", \"exercise\": \"task\", \"estimatedHours\": 2}\n" +
                "  ],\n" +
                "  \"estimatedReadiness\": \"timeline estimate\"\n" +
                "}\n\n" +
                "RULES:\n" +
                "- prosAndCons MUST have one entry per scored category (never empty)\n" +
                "- roadmap MUST have 3-7 days covering gaps and areas to improve from score < 8\n" +
                "- If all scores are 10, provide advanced topics to master\n" +
                "- resumeConsistencyForCandidate: list 3-5 key skills from JD and whether demonstrated\n" +
                "- Use real resource URLs (official docs, tutorials)\n";

            String user = "Role: " + req.getJdTitle() + "\n" +
                "Summary: " + summary + "\n" +
                "Scores:\n" + scoresInfo + "\n" +
                "JD (first 300 chars): " + (req.getJdText() != null ? req.getJdText().substring(0, Math.min(300, req.getJdText().length())) : "") + "\n";

            String raw = llmClient.chatAssessmentWithTracking(system, user, req.getInterviewId(), userId);
            JsonNode feedbackJson = objectMapper.readTree(JsonRepairUtil.repair(raw));
            return parseCandidateFeedback(feedbackJson);
        } catch (Exception e) {
            log.error("Failed to generate candidate feedback separately: {}", e.getMessage());
            return Map.of(
                "summary", summary,
                "overallSummary", summary,
                "prosAndCons", List.of(),
                "strengths", List.of(),
                "areasToImprove", List.of(),
                "resumeConsistencyForCandidate", List.of(),
                "roadmap", List.of(),
                "estimatedReadiness", "Unable to generate detailed feedback",
                "estimatedReadinessTimeline", "Unable to generate detailed feedback"
            );
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseCategories(String rubricJson) {
        if (rubricJson == null || rubricJson.isBlank()) return defaultCategories();
        try {
            JsonNode node = objectMapper.readTree(rubricJson);
            JsonNode cats = node.path("categories");
            if (cats.isArray() && cats.size() > 0) {
                List<Map<String, Object>> list = new ArrayList<>();
                cats.forEach(c -> {
                    Map<String, Object> cat = new LinkedHashMap<>();
                    cat.put("key", c.path("key").asText());
                    cat.put("label", c.path("label").asText());
                    cat.put("subSkill", c.path("subSkill").asText(c.path("label").asText()));
                    cat.put("description", c.path("description").asText());
                    cat.put("weight", c.path("weight").asInt(2));
                    String priority = c.path("priority").asText("");
                    if (priority.isBlank()) {
                        priority = c.path("weight").asInt(2) >= 3 ? "MUST_HAVE" : "GOOD_TO_HAVE";
                    }
                    cat.put("priority", priority);
                    cat.put("note", c.path("note").asText(""));
                    List<String> options = toStringList(c.path("proficiencyOptions"));
                    if (options.size() != 4) {
                        options = List.of(
                            "Strong knowledge of " + cat.get("subSkill"),
                            "Good knowledge of " + cat.get("subSkill"),
                            "Only theoretical knowledge of " + cat.get("subSkill") + " with no practical experience",
                            "No knowledge of " + cat.get("subSkill")
                        );
                    }
                    cat.put("proficiencyOptions", options);
                    list.add(cat);
                });
                return list;
            }
        } catch (Exception ignored) {}
        return defaultCategories();
    }

    private List<Map<String, Object>> defaultCategories() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(rubricCategory("coreJava", "Java", "Java Fundamentals", "OOP, collections, memory, threads", 3, "MUST_HAVE", ""));
        list.add(rubricCategory("spring", "Spring Boot", "Spring Boot Fundamentals", "IoC, REST, data, security", 3, "MUST_HAVE", ""));
        list.add(rubricCategory("microservices", "Software Architecture", "Microservices Architecture", "Service design, resilience", 2, "GOOD_TO_HAVE", ""));
        list.add(rubricCategory("miscellaneous", "Problem Solving", "Case Study Problem-Solving", "SQL, Docker, system design", 1, "GOOD_TO_HAVE", ""));
        return list;
    }

    private Map<String, Object> rubricCategory(String key, String label, String subSkill, String description,
                                               int weight, String priority, String note) {
        Map<String, Object> cat = new LinkedHashMap<>();
        cat.put("key", key);
        cat.put("label", label);
        cat.put("subSkill", subSkill);
        cat.put("description", description);
        cat.put("weight", weight);
        cat.put("priority", priority);
        cat.put("note", note);
        cat.put("proficiencyOptions", List.of(
            "Strong knowledge of " + subSkill,
            "Good knowledge of " + subSkill,
            "Only theoretical knowledge of " + subSkill + " with no practical experience",
            "No knowledge of " + subSkill
        ));
        return cat;
    }

    private Map<String, Object> parseCandidateProfile(String profileJson) {
        if (profileJson == null || profileJson.isBlank() || "null".equals(profileJson.trim())) {
            return Map.of("level", "mid", "yearsOfExperience", 0);
        }
        try {
            Map<String, Object> result = objectMapper.readValue(profileJson,
                    new com.fasterxml.jackson.core.type.TypeReference<>() {});
            return result != null ? result : Map.of("level", "mid", "yearsOfExperience", 0);
        } catch (Exception e) { return Map.of("level", "mid", "yearsOfExperience", 0); }
    }

    private Object parseObject(JsonNode node) {
        if (node.isMissingNode()) return Map.of();
        try { return objectMapper.convertValue(node, Object.class); }
        catch (Exception e) { return Map.of(); }
    }

    private Map<String, Object> withdrawnResult(String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("categoryScores", List.of());
        result.put("proposedVerdict", "WITHDRAWN");
        result.put("summary", reason);
        result.put("candidateFeedback", Map.of(
            "summary", reason,
            "overallSummary", reason,
            "prosAndCons", List.of(),
            "strengths", List.of(),
            "areasToImprove", List.of(),
            "resumeConsistencyForCandidate", List.of(),
            "roadmap", List.of(),
            "estimatedReadiness", "N/A",
            "estimatedReadinessTimeline", "N/A"
        ));
        result.put("clientBrief", Map.of(
            "overallFeedback", reason,
            "mustHaveSkills", List.of(),
            "goodToHaveSkills", List.of(),
            "questionsAsked", List.of(),
            "skillAssessments", List.of(),
            "source", "ai"
        ));
        result.put("source", "injection-terminated");
        return result;
    }

    private record CodeSubmissionSummary(
            String question,
            String language,
            int testsPassed,
            int testsTotal,
            int aiScore,
            String correctness,
            String overallFeedback
    ) {}

    private boolean hasSubstantiveCodeSubmissions(String codeSubmissionJson) {
        return !parseCodeSubmissionSummaries(codeSubmissionJson).isEmpty();
    }

    private List<CodeSubmissionSummary> parseCodeSubmissionSummaries(String codeSubmissionJson) {
        if (codeSubmissionJson == null || codeSubmissionJson.isBlank()) return List.of();
        try {
            JsonNode root = objectMapper.readTree(codeSubmissionJson);
            List<CodeSubmissionSummary> list = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode sub : root) list.add(toCodeSummary(sub));
            } else {
                list.add(toCodeSummary(root));
            }
            return list.stream()
                    .filter(s -> s.testsTotal() > 0 || s.aiScore() > 0
                            || "correct".equalsIgnoreCase(s.correctness())
                            || "partial".equalsIgnoreCase(s.correctness()))
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to parse code submissions for assessment: {}", e.getMessage());
            return List.of();
        }
    }

    private CodeSubmissionSummary toCodeSummary(JsonNode sub) {
        String question = sub.path("question").asText("");
        String language = sub.path("language").asText("unknown");
        int passed = 0;
        int total = 0;
        JsonNode results = sub.path("results");
        if (results.isArray()) {
            total = results.size();
            for (JsonNode r : results) {
                if (r.path("passed").asBoolean(false)) passed++;
            }
        }
        JsonNode review = sub.path("aiReview");
        int aiScore = review.path("score").asInt(0);
        String correctness = review.path("correctness").asText("unknown");
        String feedback = review.path("overallFeedback").asText("");
        return new CodeSubmissionSummary(question, language, passed, total, aiScore, correctness, feedback);
    }

    private int scoreTechnicalFromCode(List<CodeSubmissionSummary> submissions) {
        if (submissions.isEmpty()) return 1;
        int best = 1;
        for (CodeSubmissionSummary s : submissions) {
            int fromTests = 1;
            if (s.testsTotal() > 0) {
                double ratio = (double) s.testsPassed() / s.testsTotal();
                if (ratio >= 1.0) fromTests = 5;
                else if (ratio >= 0.8) fromTests = 4;
                else if (ratio >= 0.6) fromTests = 3;
                else if (ratio >= 0.4) fromTests = 2;
            }
            int fromAi = s.aiScore() > 0 ? Math.max(1, Math.min(5, s.aiScore())) : fromTests;
            int combined = Math.max(fromTests, fromAi);
            if ("correct".equalsIgnoreCase(s.correctness())) combined = Math.max(combined, 4);
            else if ("partial".equalsIgnoreCase(s.correctness())) combined = Math.max(combined, 3);
            best = Math.max(best, combined);
        }
        return Math.min(5, best);
    }

    private int scoreCommunicationForThinTranscript(long candidateWords, long candidateTurns) {
        if (candidateTurns == 0 && candidateWords == 0) return 1;
        if (candidateWords < 20) return 1;
        return 2;
    }

    private String proposeVerdictForCodingOnly(int techScore, int commScore) {
        if (techScore >= 4 && commScore <= 2) return "NEEDS_1_WEEK_PREP";
        if (techScore >= 3) return "NEEDS_1_WEEK_PREP";
        return "NEEDS_RESKILLING";
    }

    private Map<String, Object> codingOnlyAssessment(
            AssessmentRequest req,
            List<Map<String, String>> utterances,
            long candidateWords,
            long candidateTurns) {
        List<CodeSubmissionSummary> submissions = parseCodeSubmissionSummaries(req.getCodeSubmissionJson());
        int techScore = scoreTechnicalFromCode(submissions);
        int commScore = scoreCommunicationForThinTranscript(candidateWords, candidateTurns);

        CodeSubmissionSummary primary = submissions.get(0);
        int totalPassed = submissions.stream().mapToInt(CodeSubmissionSummary::testsPassed).sum();
        int totalTests = submissions.stream().mapToInt(CodeSubmissionSummary::testsTotal).sum();
        String testSummary = totalTests > 0 ? totalPassed + "/" + totalTests + " automated tests passed" : "code submitted (tests not run)";

        String techRationale = "Coding-only session: " + testSummary + ". "
                + (primary.correctness() != null && !primary.correctness().isBlank()
                ? "AI code review: " + primary.correctness() + "." : "")
                + (primary.overallFeedback() != null && !primary.overallFeedback().isBlank()
                ? " " + primary.overallFeedback().substring(0, Math.min(200, primary.overallFeedback().length()))
                : "");

        String commRationale = candidateTurns == 0
                ? "No spoken candidate responses were recorded; verbal communication and explanation could not be assessed."
                : "Very limited spoken responses (" + candidateWords + " words in " + candidateTurns
                + " turns); communication was not meaningfully assessed.";

        String summary = "Coding assessment completed (" + testSummary + "), but the verbal interview was not completed "
                + "(" + candidateWords + " words, " + candidateTurns + " candidate turns). "
                + "Technical score reflects code quality; communication and other JD topics were not verbally covered.";

        List<String> codingPros = new ArrayList<>();
        List<String> codingCons = new ArrayList<>();
        if (totalTests > 0 && totalPassed == totalTests) {
            codingPros.add("All automated test cases passed (" + testSummary + ")");
        } else if (totalPassed > 0) {
            codingPros.add("Partial test success: " + testSummary);
            codingCons.add("Some test cases failed — review edge cases and output format");
        }
        if ("correct".equalsIgnoreCase(primary.correctness())) {
            codingPros.add("AI code review rated the solution as correct");
        } else if ("partial".equalsIgnoreCase(primary.correctness())) {
            codingCons.add("AI code review: solution is partially correct");
        }
        if (primary.overallFeedback() != null && !primary.overallFeedback().isBlank()) {
            codingPros.add(primary.overallFeedback().substring(0, Math.min(160, primary.overallFeedback().length())));
        }
        if (codingPros.isEmpty()) {
            codingPros.add("Submitted a coding solution for the assigned problem");
        }

        List<Map<String, Object>> prosAndCons = new ArrayList<>();
        prosAndCons.add(Map.of(
                "category", "Coding / Problem Solving",
                "pros", codingPros,
                "cons", codingCons.isEmpty() ? List.of("Continue practicing timed coding under interview conditions") : codingCons
        ));
        prosAndCons.add(Map.of(
                "category", "Verbal Technical Interview",
                "pros", List.of(),
                "cons", List.of(
                        "No meaningful spoken answers were recorded for this session",
                        "Could not assess explanation of approach, trade-offs, or depth on JD topics (Spring, system design, etc.)"
                )
        ));
        prosAndCons.add(Map.of(
                "category", "Communication",
                "pros", List.of(),
                "cons", List.of(
                        "Communication score is low because the voice Q&A portion was not completed — not necessarily poor speaking skills",
                        "Recommend re-taking the verbal portion or a follow-up call to assess communication"
                )
        ));

        List<Map<String, Object>> scoreRows = new ArrayList<>();
        scoreRows.add(new LinkedHashMap<>(Map.of(
                "dimension", "TechnicalKnowledge",
                "value", techScore,
                "rationale", techRationale,
                "evidence", buildCodeSubmissionContext(req.getCodeSubmissionJson()),
                "gap", commScore <= 2 ? "Verbal technical depth not demonstrated in this session" : "",
                "strengths", codingPros.toString(),
                "weaknesses", codingCons.toString(),
                "confidence", totalTests > 0 && totalPassed == totalTests ? "high" : "medium"
        )));
        scoreRows.add(new LinkedHashMap<>(Map.of(
                "dimension", "communication",
                "value", commScore,
                "rationale", commRationale,
                "evidence", "",
                "gap", "Complete the voice interview to assess communication fairly",
                "strengths", "[]",
                "weaknesses", "[\"Verbal interview not completed\"]",
                "confidence", "low"
        )));

        String verdict = proposeVerdictForCodingOnly(techScore, commScore);

        Map<String, Object> candidateFeedback = new LinkedHashMap<>();
        candidateFeedback.put("summary",
                "Your coding submission was evaluated. The voice interview was not completed, so verbal and JD-wide topics were not assessed.");
        candidateFeedback.put("overallSummary", candidateFeedback.get("summary"));
        candidateFeedback.put("prosAndCons", prosAndCons);
        candidateFeedback.put("strengths", codingPros);
        candidateFeedback.put("areasToImprove", List.of(
                "Complete the full voice interview so communication and technical discussion can be scored",
                "Practice explaining your code approach aloud (complexity, edge cases, alternatives)"
        ));
        candidateFeedback.put("resumeConsistencyForCandidate", List.of(
                Map.of("claim", "Coding / problem solving", "demonstrated", techScore >= 3,
                        "note", "Demonstrated via submitted code" + (totalTests > 0 ? " (" + testSummary + ")" : "")),
                Map.of("claim", "Verbal technical interview", "demonstrated", false,
                        "note", "Not assessed — no spoken responses recorded"),
                Map.of("claim", "Communication", "demonstrated", false,
                        "note", "Not assessed — complete the voice Q&A portion")
        ));
        candidateFeedback.put("roadmap", List.of(
                Map.of("day", 1, "category", "Interview completion", "gap", "Verbal portion missing",
                        "focus", "Re-take voice interview questions", "whyItMatters",
                        "Hiring decisions require both code and communication evidence",
                        "resource", "Mock technical interview", "resourceUrl", "", "exercise",
                        "Record yourself explaining your coding solution for 5 minutes", "estimatedHours", 1)
        ));
        candidateFeedback.put("estimatedReadiness",
                techScore >= 4 ? "Strong on coding task; complete verbal interview for full readiness decision"
                        : "Complete verbal interview and strengthen areas flagged in code review");
        candidateFeedback.put("estimatedReadinessTimeline", candidateFeedback.get("estimatedReadiness"));

        if (llmClient.isConfigured()) {
            try {
                Map<String, Object> enriched = enrichCodingOnlyFeedbackWithLlm(
                        req, submissions, techScore, commScore, summary, scoreRows);
                if (enriched != null && !enriched.isEmpty()) {
                    candidateFeedback.putAll(enriched);
                }
            } catch (Exception e) {
                log.warn("Coding-only LLM feedback enrichment failed: {}", e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("categoryScores", scoreRows);
        result.put("technicalKnowledge", Map.of("score", techScore, "rationale", techRationale));
        result.put("communication", Map.of("score", commScore, "rationale", commRationale));
        result.put("proposedVerdict", verdict);
        result.put("summary", summary);
        result.put("candidateFeedback", candidateFeedback);
        result.put("behavioralSignals", Map.of(
                "ownershipLevel", "low",
                "learningAgility", "medium",
                "communicationStructure", "not_assessed",
                "confidenceCalibration", "not_assessed",
                "summary", "Behavioral signals not assessed — verbal interview incomplete"
        ));
        result.put("resumeConsistency", Map.of(
                "claimed", List.of(),
                "demonstrated", List.of("Coding / problem solving"),
                "notDemonstrated", List.of("Verbal technical interview", "Communication"),
                "consistencyScore", techScore >= 3 ? 3 : 2,
                "flags", List.of("coding_only_session")
        ));
        result.put("interviewQuality", Map.of(
                "coverageScore", 2,
                "categoriesCovered", List.of("Coding / Problem Solving"),
                "categoriesMissed", List.of("Verbal Q&A", "Communication", "JD breadth"),
                "note", "Session ended with code submission; voice portion not completed"
        ));
        result.put("source", "coding-only");
        return result;
    }

    private Map<String, Object> enrichCodingOnlyFeedbackWithLlm(
            AssessmentRequest req,
            List<CodeSubmissionSummary> submissions,
            int techScore,
            int commScore,
            String summary,
            List<Map<String, Object>> scoreRows) throws Exception {
        String codeContext = buildCodeSubmissionContext(req.getCodeSubmissionJson());
        String system =
                "You enrich assessment feedback for a coding-only interview (no verbal answers).\n"
                + "Return ONLY raw JSON:\n"
                + "{\n"
                + "  \"summary\": \"2-3 sentences for reviewers\",\n"
                + "  \"prosAndCons\": [{\"category\": \"name\", \"pros\": [\"...\"], \"cons\": [\"...\"]}]\n"
                + "}\n"
                + "RULES:\n"
                + "- Include categories: Coding / Problem Solving, Verbal Technical Interview, Communication\n"
                + "- Credit code quality and test results; clearly state verbal/JD topics were NOT assessed\n"
                + "- Do not penalize communication as if they spoke poorly — they did not complete voice Q&A";

        String user = "Role: " + req.getJdTitle() + "\n"
                + "Technical score: " + techScore + "/10, Communication: " + commScore + "/10 (not assessable — no speech)\n"
                + "Summary: " + summary + "\n"
                + "Code submissions:\n" + codeContext;

        String raw = llmClient.chatAssessmentWithTracking(system, user, req.getInterviewId(), "coding-only-feedback");
        JsonNode json = objectMapper.readTree(JsonRepairUtil.repair(raw));
        Map<String, Object> out = new LinkedHashMap<>();
        if (!json.path("summary").asText("").isBlank()) {
            out.put("summary", json.path("summary").asText());
            out.put("overallSummary", json.path("summary").asText());
        }
        JsonNode pc = json.path("prosAndCons");
        if (pc.isArray() && pc.size() > 0) {
            List<Map<String, Object>> list = new ArrayList<>();
            pc.forEach(n -> {
                List<String> pros = new ArrayList<>();
                List<String> cons = new ArrayList<>();
                if (n.path("pros").isArray()) n.path("pros").forEach(p -> pros.add(p.asText()));
                if (n.path("cons").isArray()) n.path("cons").forEach(c -> cons.add(c.asText()));
                list.add(Map.of(
                        "category", n.path("category").asText("General"),
                        "pros", pros,
                        "cons", cons
                ));
            });
            out.put("prosAndCons", list);
        }
        return out;
    }

    private Map<String, Object> thinTranscriptResult(String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("categoryScores", List.of());
        result.put("proposedVerdict", "NEEDS_RESKILLING");
        result.put("summary", reason);
        result.put("candidateFeedback", Map.of(
            "summary", "Not enough responses were recorded to generate feedback.",
            "overallSummary", "Not enough responses were recorded to generate feedback.",
            "prosAndCons", List.of(),
            "strengths", List.of(),
            "areasToImprove", List.of(),
            "resumeConsistencyForCandidate", List.of(),
            "roadmap", List.of(),
            "estimatedReadiness", "Unable to assess",
            "estimatedReadinessTimeline", "Unable to assess"
        ));
        result.put("source", "thin-transcript");
        return result;
    }
    
    private Map<String, Object> poorTranscriptionResult(String reason, double qualityScore) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("categoryScores", List.of());
        result.put("proposedVerdict", "NEEDS_RESKILLING");
        result.put("summary", reason + " Quality score: " + String.format("%.2f", qualityScore));
        result.put("transcriptionQualityScore", qualityScore);
        result.put("candidateFeedback", Map.of(
            "summary", "The audio quality or transcription accuracy was too low to generate reliable feedback. Please retake the interview with better audio setup.",
            "overallSummary", "The audio quality or transcription accuracy was too low to generate reliable feedback.",
            "prosAndCons", List.of(),
            "strengths", List.of(),
            "areasToImprove", List.of("Ensure clear audio", "Use a good microphone", "Minimize background noise"),
            "resumeConsistencyForCandidate", List.of(),
            "roadmap", List.of(),
            "estimatedReadiness", "Unable to assess due to poor transcription quality",
            "estimatedReadinessTimeline", "Unable to assess due to poor transcription quality"
        ));
        result.put("source", "poor-transcription");
        return result;
    }
    
    private double assessTranscriptionQuality(List<Map<String, String>> utterances) {
        List<Map<String, String>> candidateUtterances = utterances.stream()
            .filter(u -> "CANDIDATE".equals(u.get("speaker")))
            .toList();
        
        if (candidateUtterances.isEmpty()) return 0.0;
        
        int totalUtterances = candidateUtterances.size();
        int qualityIssues = 0;
        
        // Patterns indicating poor transcription
        String[] poorTranscriptionPatterns = {
            "\\b(centigrade|airms|athletics)\\b",  // Common STT errors
            "\\b[a-z]{15,}\\b",  // Very long words without spaces (STT concatenation)
            "[^\\s]{30,}",  // 30+ chars without space
            "\\b(uh|um|ah){3,}\\b",  // Excessive filler words in sequence
            "\\b\\w\\s\\w\\s\\w\\b"  // Single letters with spaces (broken words)
        };
        
        for (Map<String, String> utterance : candidateUtterances) {
            String text = utterance.get("text").toLowerCase();
            boolean hasIssue = false;

            // Check for poor transcription patterns
            for (String pattern : poorTranscriptionPatterns) {
                if (text.matches(".*" + pattern + ".*")) {
                    hasIssue = true;
                    break;
                }
            }

            // Very short responses only matter when most turns are short
            if (!hasIssue && text.split("\\s+").length < 5) {
                hasIssue = true;
            }

            if (hasIssue) {
                qualityIssues++;
            }
        }
        
        // Quality score: 1.0 = perfect, 0.0 = completely garbled
        double qualityScore = 1.0 - ((double) qualityIssues / totalUtterances);
        return Math.max(0.0, Math.min(1.0, qualityScore));
    }

    private Map<String, Object> heuristicAssessment(List<Map<String, String>> utterances) {
        long words = utterances.stream().filter(u -> "CANDIDATE".equals(u.get("speaker")))
            .mapToLong(u -> u.get("text").split("\\s+").length).sum();
        long turns = utterances.stream().filter(u -> "CANDIDATE".equals(u.get("speaker"))).count();
        int techScore = (int) Math.max(1, Math.min(5, 1 + words / 150));
        int commScore = (int) Math.max(1, Math.min(5, turns));
        
        log.warn("Using heuristic assessment - candidate words: {}, turns: {}, techScore: {}, commScore: {}", 
                words, turns, techScore, commScore);
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("categoryScores", List.of(
            Map.of("dimension", "coreJava", "value", techScore, "rationale", "Heuristic assessment - Claude API failed or not configured. Words: " + words + ", Turns: " + turns,
                "evidence", "", "gap", "", "strengths", "[]", "weaknesses", "[]", "confidence", "low"),
            Map.of("dimension", "communication", "value", commScore, "rationale", "Heuristic assessment - Claude API failed or not configured. Turns: " + turns,
                "evidence", "", "gap", "", "strengths", "[]", "weaknesses", "[]", "confidence", "low")
        ));
        result.put("scoreMax", 5);
        result.put("proposedVerdict", "NEEDS_1_WEEK_PREP");
        result.put("summary", "Heuristic assessment used - Claude API unavailable or failed. Candidate provided " + words + " words in " + turns + " responses.");
        result.put("candidateFeedback", Map.of(
            "summary", "AI assessment not available - using basic word/turn count analysis.",
            "overallSummary", "AI assessment not available - using basic word/turn count analysis.",
            "prosAndCons", List.of(),
            "strengths", List.of(),
            "areasToImprove", List.of(),
            "resumeConsistencyForCandidate", List.of(),
            "roadmap", List.of(),
            "estimatedReadiness", "Unable to assess without AI analysis",
            "estimatedReadinessTimeline", "Unable to assess without AI analysis"
        ));
        result.put("source", "heuristic");
        return result;
    }

    private List<Map<String, String>> parseUtterances(String transcriptJson) {
        if (transcriptJson == null || transcriptJson.isBlank()) return List.of();
        try {
            JsonNode doc = objectMapper.readTree(transcriptJson);
            JsonNode arr = doc.path("utterances");
            List<Map<String, String>> list = new ArrayList<>();
            if (arr.isArray())
                for (JsonNode u : arr)
                    list.add(Map.of("speaker", u.path("speaker").asText("CANDIDATE"), "text", u.path("text").asText("")));
            return list;
        } catch (Exception e) { return List.of(); }
    }

    private String buildCodeSubmissionContext(String codeSubmissionJson) {
        if (codeSubmissionJson == null || codeSubmissionJson.isBlank()) return "";
        try {
            JsonNode root = objectMapper.readTree(codeSubmissionJson);
            StringBuilder sb = new StringBuilder();
            if (root.isArray()) {
                int i = 0;
                for (JsonNode sub : root) {
                    appendCodeSubmissionEntry(sb, sub, ++i);
                }
            } else {
                appendCodeSubmissionEntry(sb, root, 1);
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("Failed to parse codeSubmissionJson: {}", e.getMessage());
            return "";
        }
    }

    private void appendCodeSubmissionEntry(StringBuilder sb, JsonNode sub, int index) {
        String question = sub.path("question").asText("");
        String language = sub.path("language").asText("unknown");
        String code = sub.path("code").asText("");
        sb.append("Submission ").append(index);
        if (!question.isBlank()) {
            sb.append(" (Q: ").append(question, 0, Math.min(120, question.length())).append(")");
        }
        sb.append(" [").append(language).append("]:\n");
        if (!code.isBlank()) {
            sb.append(code, 0, Math.min(2000, code.length())).append("\n");
        }
        JsonNode results = sub.path("results");
        if (results.isArray() && results.size() > 0) {
            int passed = 0;
            for (JsonNode r : results) if (r.path("passed").asBoolean(false)) passed++;
            sb.append("Tests: ").append(passed).append("/").append(results.size()).append(" passed\n");
        }
        JsonNode review = sub.path("aiReview");
        if (!review.isMissingNode()) {
            sb.append("AI review score: ").append(review.path("score").asInt(0)).append("/10, ")
              .append("correctness: ").append(review.path("correctness").asText("unknown")).append("\n");
            String feedback = review.path("overallFeedback").asText("");
            if (!feedback.isBlank()) {
                sb.append("Feedback: ").append(feedback, 0, Math.min(300, feedback.length())).append("\n");
            }
        }
        sb.append("\n");
    }

    private List<String> toStringList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node.isArray()) node.forEach(n -> list.add(n.asText()));
        return list;
    }

    private List<Map<String, String>> deduplicateUtterances(List<Map<String, String>> utterances) {
        if (utterances.size() <= 1) return utterances;
        
        List<Map<String, String>> result = new ArrayList<>();
        result.add(utterances.get(0)); // Always keep first
        
        for (int i = 1; i < utterances.size(); i++) {
            Map<String, String> current = utterances.get(i);
            Map<String, String> previous = utterances.get(i - 1);
            
            String currentText = current.get("text");
            String previousText = previous.get("text");
            
            // Calculate similarity ratio
            double similarity = calculateSimilarity(currentText, previousText);
            
            if (similarity < 0.8) {
                result.add(current); // Keep if not too similar
            } else {
                // Keep the longer one if very similar
                if (currentText.length() > previousText.length()) {
                    result.set(result.size() - 1, current);
                }
            }
        }
        
        return result;
    }

    private double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        if (s1.equals(s2)) return 1.0;
        
        String[] words1 = s1.toLowerCase().split("\\s+");
        String[] words2 = s2.toLowerCase().split("\\s+");
        
        Set<String> set1 = new HashSet<>(List.of(words1));
        Set<String> set2 = new HashSet<>(List.of(words2));
        
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);
        
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private String getVerdictRulesForMode(String mode) {
        return switch (mode != null ? mode : "L3") {
            case "SCREENING" -> "  READY: avg >= 6\n  NEEDS_1_WEEK_PREP: avg >= 4\n  NEEDS_RESKILLING: avg < 4\n  MISMATCH_WITH_JD: evidence doesn't match JD domain\n  WITHDRAWN: candidate ended early";
            case "L1" -> "  READY: avg >= 7\n  NEEDS_1_WEEK_PREP: avg >= 5\n  NEEDS_RESKILLING: avg < 5\n  MISMATCH_WITH_JD: evidence doesn't match JD domain\n  WITHDRAWN: candidate ended early";
            case "L2" -> "  READY: avg >= 8\n  NEEDS_1_WEEK_PREP: avg >= 6\n  NEEDS_RESKILLING: avg < 6\n  MISMATCH_WITH_JD: evidence doesn't match JD domain\n  WITHDRAWN: candidate ended early";
            case "L3" -> "  READY: avg >= 8\n  NEEDS_1_WEEK_PREP: avg >= 7\n  NEEDS_RESKILLING: avg < 7\n  MISMATCH_WITH_JD: evidence doesn't match JD domain\n  WITHDRAWN: candidate ended early";
            case "L4" -> "  READY: avg >= 9\n  NEEDS_1_WEEK_PREP: avg >= 8\n  NEEDS_RESKILLING: avg < 8\n  MISMATCH_WITH_JD: evidence doesn't match JD domain\n  WITHDRAWN: candidate ended early";
            default -> "  READY: avg >= 8, communication >= 8\n  NEEDS_1_WEEK_PREP: avg >= 6\n  NEEDS_RESKILLING: avg < 6\n  MISMATCH_WITH_JD: evidence doesn't match JD domain\n  WITHDRAWN: candidate ended early";
        };
    }

    private void storeAssessmentResponse(String interviewId, String assessmentJson, int tokensUsed, String source, String userId) {
        try {
            Map<String, Object> request = Map.of(
                "interviewId", interviewId,
                "assessmentJson", assessmentJson,
                "tokensUsed", tokensUsed,
                "assessmentSource", source
            );
            
            complianceServiceClient.storeAssessmentResponse(request, userId != null ? userId : "system");
            log.info("Successfully stored assessment response for interview {}", interviewId);
        } catch (Exception e) {
            log.error("Error storing assessment response: {}", e.getMessage());
        }
    }

    private void finalizeInterviewTokens(String interviewId, String userId) {
        try {
            Map<String, String> request = Map.of("interviewId", interviewId);
            complianceServiceClient.finalizeInterviewTokens(request, userId != null ? userId : "system");
            log.info("Successfully finalized token summary for interview {}", interviewId);
        } catch (Exception e) {
            log.error("Error finalizing interview tokens: {}", e.getMessage());
        }
    }

    private int calculateTotalTokensUsed(String interviewId) {
        // Token tracking is handled by the active LLM client implementation (Claude/Ollama).
        // We return 0 here because the compliance service tracks per-operation totals independently.
        return 0;
    }
}
