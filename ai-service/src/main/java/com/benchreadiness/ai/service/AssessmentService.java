package com.benchreadiness.ai.service;

import com.benchreadiness.ai.client.ComplianceServiceClient;
import com.benchreadiness.ai.dto.AssessmentRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class AssessmentService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AssessmentService.class);

    private final LlmClient llmClient;
    private final RubricService rubricService;
    private final ComplianceServiceClient complianceServiceClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, CachedAssessmentResult> assessmentCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> idempotencyHitCountByInterview = new ConcurrentHashMap<>();
    private static final long ASSESSMENT_CACHE_TTL_MS = 24 * 60 * 60 * 1000; // 24 hours

    private record CachedAssessmentResult(String payloadJson, long createdAtMs) {}

    public AssessmentService(LlmClient llmClient, RubricService rubricService, ComplianceServiceClient complianceServiceClient) {
        this.llmClient = llmClient;
        this.rubricService = rubricService;
        this.complianceServiceClient = complianceServiceClient;
    }

    public Map<String, Object> assess(AssessmentRequest req, String userId) {
        String cacheKey = buildAssessmentCacheKey(req);
        Map<String, Object> cached = getCachedAssessment(cacheKey);
        if (cached != null) {
            log.info("Returning cached assessment for interview {}", req.getInterviewId());
            incrementIdempotencyHit(req.getInterviewId());
            return cached;
        }

        List<Map<String, String>> utterances = parseUtterances(req.getTranscriptJson());
        long candidateWords = utterances.stream()
            .filter(u -> "CANDIDATE".equals(u.get("speaker")))
            .mapToLong(u -> u.get("text").split("\\s+").length).sum();
        long candidateTurns = utterances.stream()
            .filter(u -> "CANDIDATE".equals(u.get("speaker"))).count();
            
        log.info("Assessment request for interview {}: {} candidate words, {} candidate turns", 
                req.getInterviewId(), candidateWords, candidateTurns);
            
        // Skip Claude for insufficient responses
        if (candidateWords < 50 || candidateTurns < 3) {
            log.warn("Insufficient responses for interview {}: {} words, {} turns", 
                    req.getInterviewId(), candidateWords, candidateTurns);
            Map<String, Object> result = thinTranscriptResult("Insufficient responses — candidate answered fewer than 3 questions or provided less than 50 words total.");
            cacheAssessment(cacheKey, result);
            return result;
        }
        
        if (!llmClient.isConfigured()) {
            log.warn("LLM provider not configured - falling back to heuristic assessment");
            Map<String, Object> result = heuristicAssessment(utterances);
            cacheAssessment(cacheKey, result);
            return result;
        }
        try {
            log.info("Starting two-pass LLM assessment for interview: {}", req.getInterviewId());
            Map<String, Object> result = twoPassAssessment(req, utterances, userId);
            cacheAssessment(cacheKey, result);
            return result;
        } catch (Exception e) {
            log.error("LLM assessment failed for interview {}: {}", req.getInterviewId(), e.getMessage(), e);
            Map<String, Object> result = heuristicAssessment(utterances);
            cacheAssessment(cacheKey, result);
            return result;
        }
    }

    private String buildAssessmentCacheKey(AssessmentRequest req) {
        String interviewId = req.getInterviewId() != null ? req.getInterviewId() : "unknown";
        String material = String.join("|",
            req.getTranscriptJson() != null ? req.getTranscriptJson() : "",
            req.getRubricJson() != null ? req.getRubricJson() : "",
            req.getCandidateProfileJson() != null ? req.getCandidateProfileJson() : "",
            req.getJdTitle() != null ? req.getJdTitle() : "",
            req.getInterviewMode() != null ? req.getInterviewMode() : ""
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

    private void incrementIdempotencyHit(String interviewId) {
        if (interviewId == null || interviewId.isBlank()) return;
        idempotencyHitCountByInterview.computeIfAbsent(interviewId, key -> new AtomicInteger()).incrementAndGet();
    }

    public int getIdempotencyHits(String interviewId) {
        if (interviewId == null || interviewId.isBlank()) return 0;
        AtomicInteger count = idempotencyHitCountByInterview.get(interviewId);
        return count != null ? count.get() : 0;
    }

    // ── Pass 1: Evidence extraction ──────────────────────────────────────────
    private Map<String, List<String>> extractEvidence(
            List<Map<String, String>> utterances, List<Map<String, Object>> categories, String interviewId, String userId) throws Exception {

        String categoryList = categories.stream()
            .map(c -> "- " + c.get("key") + ": " + c.get("description"))
            .reduce("", (a, b) -> a + "\n" + b);

        String system =
            "Extract evidence from the interview transcript for each category.\n" +
            "Return ONLY valid JSON: {\"categoryKey\": [\"evidence1\", \"evidence2\"], ...}\n" +
            "Each item is a direct quote or close paraphrase (max 25 words).\n" +
            "If nothing was said about a category, return an empty array.\n" +
            "Categories:\n" + categoryList;

        String transcript = buildEfficientTranscript(utterances);
        String raw = llmClient.chatAssessmentWithTracking(system, "Transcript:\n" + transcript, interviewId, userId);
        JsonNode json = objectMapper.readTree(raw);

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
            evidence = extractEvidence(utterances, categories, req.getInterviewId(), userId);
            log.info("Evidence extraction completed for {} categories", evidence.size());
        } catch (Exception e) {
            log.error("Evidence extraction failed: {}", e.getMessage(), e);
            throw e;
        }

        String categoryScoreSchema = categories.stream()
            .map(c -> "    \"" + c.get("key") + "\": {\n" +
                "      \"score\": 1-5 or null,\n" +
                "      \"strengths\": [\"specific thing they did well\"],\n" +
                "      \"weaknesses\": [\"specific thing they got wrong or missed\"],\n" +
                "      \"evidence\": \"direct quote or paraphrase\",\n" +
                "      \"gap\": \"specific topics missing\",\n" +
                "      \"confidence\": \"low|medium|high\"\n" +
                "    }")
            .reduce("", (a, b) -> a + "\n" + b);

        String evidenceSummary = evidence.entrySet().stream()
            .map(e -> e.getKey() + ": " + (e.getValue().isEmpty() ? "no evidence" : String.join("; ", e.getValue())))
            .reduce("", (a, b) -> a + "\n" + b);

        String level = (String) candidateProfile.getOrDefault("level", "mid");
        String yoe = String.valueOf(candidateProfile.getOrDefault("yearsOfExperience", "unknown"));
        Object claimedRaw = candidateProfile.get("claimedExpertise");
        String claimed = claimedRaw != null ? claimedRaw.toString() : "[]";

        String system =
            "You are an expert technical hiring assessor. Produce a thorough, evidence-based evaluation.\n" +
            "Candidate: " + level + " level, " + yoe + " years experience.\n\n" +
            "SCORING: 5=expert, 4=solid, 3=surface-level, 2=partial, 1=none, null=not discussed. confidence: low/medium/high.\n" +
            "VERDICT: " + getVerdictRulesForMode(req.getInterviewMode()) + "\n\n" +
            "Return ONLY valid JSON with ALL sections populated (no empty arrays):\n" +
            "{\n" +
            "  \"categoryScores\": {\n" + categoryScoreSchema + "\n  },\n" +
            "  \"communication\": {\"score\": 1-5, \"rationale\": \"\"},\n" +
            "  \"proposedVerdict\": \"READY|NEEDS_1_WEEK_PREP|NEEDS_RESKILLING|MISMATCH_WITH_JD\",\n" +
            "  \"summary\": \"2-3 sentences for manager\",\n" +
            "  \"resumeConsistency\": {\"claimed\": [], \"demonstrated\": [], \"notDemonstrated\": [], \"consistencyScore\": 1-5, \"flags\": []},\n" +
            "  \"behavioralSignals\": {\"ownershipLevel\": \"low|medium|high\", \"learningAgility\": \"low|medium|high\", \"communicationStructure\": \"low|medium|high\", \"confidenceCalibration\": \"low|medium|high\", \"summary\": \"\"},\n" +
            "  \"interviewQuality\": {\"coverageScore\": 1-5, \"categoriesCovered\": [], \"categoriesMissed\": [], \"note\": \"\"},\n" +
            "  \"candidateFeedback\": {\n" +
            "    \"summary\": \"2-3 sentences for candidate\",\n" +
            "    \"prosAndCons\": [{\"category\": \"name\", \"pros\": [\"...\"], \"cons\": [\"...\"]}],\n" +
            "    \"resumeConsistencyForCandidate\": [{\"claim\": \"skill\", \"demonstrated\": true, \"note\": \"...\"}],\n" +
            "    \"roadmap\": [{\"day\": 1, \"category\": \"name\", \"gap\": \"gap\", \"focus\": \"topic\", \"whyItMatters\": \"reason\", \"resource\": \"name\", \"resourceUrl\": \"https://url\", \"exercise\": \"task\", \"estimatedHours\": 2}],\n" +
            "    \"estimatedReadiness\": \"\"\n" +
            "  }\n" +
            "}\n\n" +
            "CRITICAL RULES:\n" +
            "- prosAndCons: ONE entry per scored category (NEVER empty)\n" +
            "- roadmap: 3-7 days for any category with score < 5. If all 5, provide advanced topics\n" +
            "- resumeConsistencyForCandidate: 3-5 key JD skills, mark demonstrated true/false\n" +
            "- Use real resource URLs (official docs preferred)\n";

        String user = "Role: " + req.getJdTitle() + "\n" +
            "JD:\n" + req.getJdText().substring(0, Math.min(500, req.getJdText().length())) + "\n" +
            "Resume (claimed skills):\n" + (req.getResumeSummary() != null
                ? req.getResumeSummary().substring(0, Math.min(500, req.getResumeSummary().length())) : "") + "\n" +
            "Claimed expertise from profile: " + claimed + "\n" +
            "\nExtracted evidence per category:\n" + evidenceSummary;

        log.info("Starting final assessment pass with system prompt length: {}, user prompt length: {}", 
                system.length(), user.length());
        
        String raw;
        try {
            raw = llmClient.chatAssessmentWithTracking(system, user, req.getInterviewId(), userId);
            log.info("Final assessment completed, response length: {}", raw.length());
        } catch (Exception e) {
            log.error("Final assessment API call failed: {}", e.getMessage(), e);
            throw e;
        }
        
        JsonNode json;
        try {
            json = objectMapper.readTree(raw);
            log.info("Assessment JSON parsed successfully");
        } catch (com.fasterxml.jackson.core.io.JsonEOFException e) {
            log.error("Failed to parse assessment JSON response - likely truncated: {}", e.getMessage());
            log.error("Raw response (first 1000 chars): {}", raw.substring(0, Math.min(1000, raw.length())));
            
            // If JSON is truncated, try a shorter assessment
            if (raw.length() > 500 && (raw.contains("categoryScores") || raw.contains("springBootProficiency"))) {
                log.info("Attempting recovery with shorter assessment prompt...");
                try {
                    String shorterSystem = createShorterAssessmentPrompt(categories, req.getInterviewMode());
                    String shorterUser = "Role: " + req.getJdTitle() + "\n" +
                        "Evidence:\n" + evidenceSummary.substring(0, Math.min(800, evidenceSummary.length()));
                    
                    String retryRaw = llmClient.chatAssessmentWithTracking(shorterSystem, shorterUser, req.getInterviewId(), userId);
                    json = objectMapper.readTree(retryRaw);
                    log.info("Recovery assessment successful");
                } catch (Exception retryE) {
                    log.error("Recovery assessment also failed: {}", retryE.getMessage());
                    throw e; // Throw original exception
                }
            } else {
                throw e;
            }
        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            log.error("Failed to parse assessment JSON response - parse error: {}", e.getMessage());
            log.error("Raw response (first 1000 chars): {}", raw.substring(0, Math.min(1000, raw.length())));
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse assessment JSON response: {}", e.getMessage());
            log.error("Raw response (first 500 chars): {}", raw.substring(0, Math.min(500, raw.length())));
            throw e;
        }
        
        Map<String, Object> result = buildResult(json, categories, evidence, req, userId);
        applyReadinessGates(result, categories, evidence, req.getInterviewMode());
        
        // Store assessment response and finalize token summary
        try {
            String assessmentJson = objectMapper.writeValueAsString(result);
            int totalTokens = calculateTotalTokensUsed(req.getInterviewId());
            storeAssessmentResponse(req.getInterviewId(), assessmentJson, totalTokens, "claude-two-pass", userId);
            finalizeInterviewTokens(req.getInterviewId(), userId);
        } catch (Exception e) {
            log.warn("Failed to store assessment response for interview {}: {}", req.getInterviewId(), e.getMessage());
        }
        
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
        boolean commWeak = communication != null && communication < Math.max(2, minScoreFloor - 1);

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
            case "SCREENING" -> 2;
            case "L1" -> 3;
            case "L2" -> 3;
            case "L3" -> 4;
            case "L4" -> 4;
            default -> 3;
        };
    }

    private Map<String, Object> buildResult(JsonNode json, List<Map<String, Object>> categories,
                                             Map<String, List<String>> evidence, AssessmentRequest req, String userId) {
        Map<String, Object> result = new LinkedHashMap<>();

        List<Map<String, Object>> scoreRows = new ArrayList<>();
        JsonNode catScores = json.path("categoryScores");
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
        JsonNode comm = json.path("communication");
        scoreRows.add(Map.of(
            "dimension", "communication",
            "value", comm.path("score").asInt(3),
            "rationale", comm.path("rationale").asText(""),
            "evidence", "", "gap", "",
            "strengths", toStringList(comm.path("strengths")).toString(),
            "weaknesses", toStringList(comm.path("weaknesses")).toString(),
            "confidence", "medium"
        ));

        result.put("categoryScores", scoreRows);
        result.put("proposedVerdict", json.path("proposedVerdict").asText("NEEDS_1_WEEK_PREP"));
        result.put("summary", json.path("summary").asText());
        
        // Handle optional fields that may not be present in shorter responses
        JsonNode resumeConsistency = json.path("resumeConsistency");
        if (!resumeConsistency.isMissingNode()) {
            result.put("resumeConsistency", parseObject(resumeConsistency));
        } else {
            result.put("resumeConsistency", Map.of("claimed", List.of(), "demonstrated", List.of(), 
                "notDemonstrated", List.of(), "consistencyScore", 3, "flags", List.of()));
        }
        
        JsonNode behavioralSignals = json.path("behavioralSignals");
        if (!behavioralSignals.isMissingNode()) {
            result.put("behavioralSignals", parseObject(behavioralSignals));
        } else {
            result.put("behavioralSignals", Map.of("ownershipLevel", "medium", "learningAgility", "medium",
                "communicationStructure", "medium", "confidenceCalibration", "medium", "summary", "Assessment abbreviated"));
        }
        
        JsonNode interviewQuality = json.path("interviewQuality");
        if (!interviewQuality.isMissingNode()) {
            result.put("interviewQuality", parseObject(interviewQuality));
        } else {
            result.put("interviewQuality", Map.of("coverageScore", 3, "categoriesCovered", 
                categories.stream().map(c -> c.get("key")).toList(), "categoriesMissed", List.of(), "note", "Standard coverage"));
        }
        
        JsonNode candidateFeedback = json.path("candidateFeedback");
        if (!candidateFeedback.isMissingNode()) {
            Map<String, Object> feedback = parseCandidateFeedback(candidateFeedback);
            // Check if feedback is actually populated
            List<?> roadmap = (List<?>) feedback.get("roadmap");
            List<?> prosAndCons = (List<?>) feedback.get("prosAndCons");
            if ((roadmap == null || roadmap.isEmpty()) && (prosAndCons == null || prosAndCons.isEmpty())) {
                log.warn("candidateFeedback returned empty from Claude - generating in separate call");
                feedback = generateCandidateFeedbackSeparately(scoreRows, categories, evidence, result.get("summary").toString(), req, userId);
            }
            result.put("candidateFeedback", feedback);
        } else {
            log.warn("candidateFeedback missing from Claude response - generating separately");
            Map<String, Object> feedback = generateCandidateFeedbackSeparately(scoreRows, categories, evidence, result.get("summary").toString(), req, userId);
            result.put("candidateFeedback", feedback);
        }
        
        result.put("source", "claude-two-pass");
        return result;
    }

    private String buildEfficientTranscript(List<Map<String, String>> utterances) {
        List<Map<String, String>> candidates = utterances.stream()
            .filter(u -> "CANDIDATE".equals(u.get("speaker"))).toList();
        
        // Deduplicate consecutive similar answers
        List<Map<String, String>> deduplicated = deduplicateUtterances(candidates);
        
        int start = Math.max(0, deduplicated.size() - 8);
        StringBuilder sb = new StringBuilder();
        for (Map<String, String> ans : deduplicated.subList(start, deduplicated.size())) {
            int idx = utterances.indexOf(ans);
            if (idx > 0 && "BOT".equals(utterances.get(idx - 1).get("speaker")))
                sb.append("Q: ").append(utterances.get(idx - 1).get("text")).append("\n");
            sb.append("A: ").append(ans.get("text"), 0, Math.min(600, ans.get("text").length())).append("\n\n");
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
                scoresInfo.append(row.get("dimension")).append(": ").append(row.get("value")).append("/5")
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
                "- roadmap MUST have 3-7 days covering gaps and areas to improve from score < 5\n" +
                "- If all scores are 5, provide advanced topics to master\n" +
                "- resumeConsistencyForCandidate: list 3-5 key skills from JD and whether demonstrated\n" +
                "- Use real resource URLs (official docs, tutorials)\n";

            String user = "Role: " + req.getJdTitle() + "\n" +
                "Summary: " + summary + "\n" +
                "Scores:\n" + scoresInfo + "\n" +
                "JD (first 300 chars): " + (req.getJdText() != null ? req.getJdText().substring(0, Math.min(300, req.getJdText().length())) : "") + "\n";

            String raw = llmClient.chatAssessmentWithTracking(system, user, req.getInterviewId(), userId);
            JsonNode feedbackJson = objectMapper.readTree(raw);
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
                cats.forEach(c -> list.add(Map.of(
                    "key", c.path("key").asText(),
                    "label", c.path("label").asText(),
                    "description", c.path("description").asText(),
                    "weight", c.path("weight").asInt(2)
                )));
                return list;
            }
        } catch (Exception ignored) {}
        return defaultCategories();
    }

    private Map<String, Object> parseCandidateProfile(String profileJson) {
        if (profileJson == null || profileJson.isBlank()) return Map.of("level", "mid", "yearsOfExperience", 0);
        try {
            return objectMapper.readValue(profileJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) { return Map.of("level", "mid", "yearsOfExperience", 0); }
    }

    private List<Map<String, Object>> defaultCategories() {
        return List.of(
            Map.of("key", "coreJava", "label", "Core Java", "description", "OOP, collections, memory, threads", "weight", 3),
            Map.of("key", "spring", "label", "Spring", "description", "IoC, REST, data, security", "weight", 3),
            Map.of("key", "microservices", "label", "Microservices", "description", "Service design, resilience", "weight", 2),
            Map.of("key", "miscellaneous", "label", "Miscellaneous", "description", "SQL, Docker, system design", "weight", 1)
        );
    }

    private Object parseObject(JsonNode node) {
        if (node.isMissingNode()) return Map.of();
        try { return objectMapper.convertValue(node, Object.class); }
        catch (Exception e) { return Map.of(); }
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

    private String createShorterAssessmentPrompt(List<Map<String, Object>> categories, String interviewMode) {
        String categoryScoreSchema = categories.stream()
            .map(c -> "    \"" + c.get("key") + "\": {\"score\": 1-5, \"evidence\": \"brief quote\", \"gap\": \"missing topics\"}")
            .reduce("", (a, b) -> a + "\n" + b);

        return "You are a technical assessor. Score each category 1-5 based on evidence.\n" +
            "SCORING: 5=expert, 4=solid, 3=basic, 2=weak, 1=none\n" +
            "VERDICT: " + getVerdictRulesForMode(interviewMode) + "\n" +
            "Return ONLY valid JSON:\n" +
            "{\n" +
            "  \"categoryScores\": {\n" + categoryScoreSchema + "\n  },\n" +
            "  \"communication\": {\"score\": 1-5, \"rationale\": \"\"},\n" +
            "  \"proposedVerdict\": \"READY|NEEDS_1_WEEK_PREP|NEEDS_RESKILLING|MISMATCH_WITH_JD\",\n" +
            "  \"summary\": \"brief summary for manager\"\n" +
            "}";
    }

    private String getVerdictRulesForMode(String mode) {
        return switch (mode != null ? mode : "L3") {
            case "SCREENING" -> "  READY: avg >= 3\n  NEEDS_1_WEEK_PREP: avg >= 2\n  NEEDS_RESKILLING: avg < 2\n  MISMATCH_WITH_JD: evidence doesn't match JD domain\n  WITHDRAWN: candidate ended early";
            case "L1" -> "  READY: avg >= 3.5\n  NEEDS_1_WEEK_PREP: avg >= 2.5\n  NEEDS_RESKILLING: avg < 2.5\n  MISMATCH_WITH_JD: evidence doesn't match JD domain\n  WITHDRAWN: candidate ended early";
            case "L2" -> "  READY: avg >= 4\n  NEEDS_1_WEEK_PREP: avg >= 3\n  NEEDS_RESKILLING: avg < 3\n  MISMATCH_WITH_JD: evidence doesn't match JD domain\n  WITHDRAWN: candidate ended early";
            case "L3" -> "  READY: avg >= 4\n  NEEDS_1_WEEK_PREP: avg >= 3.5\n  NEEDS_RESKILLING: avg < 3.5\n  MISMATCH_WITH_JD: evidence doesn't match JD domain\n  WITHDRAWN: candidate ended early";
            case "L4" -> "  READY: avg >= 4.5\n  NEEDS_1_WEEK_PREP: avg >= 4\n  NEEDS_RESKILLING: avg < 4\n  MISMATCH_WITH_JD: evidence doesn't match JD domain\n  WITHDRAWN: candidate ended early";
            default -> "  READY: avg >= 4, communication >= 4\n  NEEDS_1_WEEK_PREP: avg >= 3\n  NEEDS_RESKILLING: avg < 3\n  MISMATCH_WITH_JD: evidence doesn't match JD domain\n  WITHDRAWN: candidate ended early";
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
