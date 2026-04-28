package com.benchreadiness.ai.service;

import com.benchreadiness.ai.dto.AssessmentRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AssessmentService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AssessmentService.class);

    private final OpenAiClient openAiClient;
    private final RubricService rubricService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AssessmentService(OpenAiClient openAiClient, RubricService rubricService) {
        this.openAiClient = openAiClient;
        this.rubricService = rubricService;
    }

    public Map<String, Object> assess(AssessmentRequest req) {
        List<Map<String, String>> utterances = parseUtterances(req.getTranscriptJson());
        long candidateWords = utterances.stream()
            .filter(u -> "CANDIDATE".equals(u.get("speaker")))
            .mapToLong(u -> u.get("text").split("\\s+").length).sum();
        long candidateTurns = utterances.stream()
            .filter(u -> "CANDIDATE".equals(u.get("speaker"))).count();
            
        // Skip Claude for insufficient responses
        if (candidateWords < 50 || candidateTurns < 3) {
            return thinTranscriptResult("Insufficient responses — candidate answered fewer than 3 questions or provided less than 50 words total.");
        }
        
        if (!openAiClient.isConfigured()) return heuristicAssessment(utterances);
        try {
            return twoPassAssessment(req, utterances);
        } catch (Exception e) {
            log.warn("LLM assessment failed: {}", e.getMessage());
            return heuristicAssessment(utterances);
        }
    }

    // ── Pass 1: Evidence extraction ──────────────────────────────────────────
    private Map<String, List<String>> extractEvidence(
            List<Map<String, String>> utterances, List<Map<String, Object>> categories) throws Exception {

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
        String raw = openAiClient.chatQuestion(system, "Transcript:\n" + transcript);
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
            AssessmentRequest req, List<Map<String, String>> utterances) throws Exception {

        List<Map<String, Object>> categories;
        if (req.getRubricJson() == null || req.getRubricJson().isBlank()) {
            log.info("rubricJson not provided — generating from JD");
            try {
                com.benchreadiness.ai.dto.RubricRequest rubricReq = new com.benchreadiness.ai.dto.RubricRequest();
                rubricReq.setJdTitle(req.getJdTitle());
                rubricReq.setJdText(req.getJdText());
                rubricReq.setResumeSummary(req.getResumeSummary() != null ? req.getResumeSummary() : "");
                Map<String, Object> generated = rubricService.generateRubric(rubricReq);
                categories = parseCategories(objectMapper.writeValueAsString(generated.get("rubric")));
            } catch (Exception e) {
                log.warn("On-the-fly rubric generation failed: {}", e.getMessage());
                categories = defaultCategories();
            }
        } else {
            categories = parseCategories(req.getRubricJson());
        }

        Map<String, Object> candidateProfile = parseCandidateProfile(req.getCandidateProfileJson());
        Map<String, List<String>> evidence = extractEvidence(utterances, categories);

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
            "Candidate: " + level + " level, " + yoe + " years experience. Hold to that standard.\n\n" +
            "SCORING RULES:\n" +
            "- 5: Expert depth, concrete examples, handles edge cases\n" +
            "- 4: Solid knowledge, minor gaps\n" +
            "- 3: Familiar but surface-level, limited concrete evidence\n" +
            "- 2: Partial knowledge, significant gaps\n" +
            "- 1: Little to no relevant evidence\n" +
            "- null: Topic not discussed at all\n" +
            "- confidence: low=1 evidence item, medium=2-3, high=4+\n\n" +
            "VERDICT RULES:\n" +
            getVerdictRulesForMode(req.getInterviewMode()) + "\n\n" +
            "ROADMAP RULES:\n" +
            "- Only include days for categories where score < 4\n" +
            "- Order by severity: lowest score = Day 1\n" +
            "- Each day must reference the specific weakness found\n" +
            "- whyItMatters: explain why this gap matters for the role\n" +
            "- resourceUrl: real, working URL to free resource (official docs preferred)\n" +
            "- exercise: hands-on task, not just reading\n\n" +
            "PROS AND CONS:\n" +
            "- prosAndCons: one entry per category that was assessed\n" +
            "- pros: specific things they demonstrated well (quote from transcript)\n" +
            "- cons: specific things they got wrong or missed (quote from transcript)\n\n" +
            "RESUME CONSISTENCY:\n" +
            "- resumeConsistencyForCandidate: cover ALL claimed skills from resume\n" +
            "- demonstrated: true/false based on interview evidence\n" +
            "- note: brief explanation\n\n" +
            "Return ONLY valid JSON:\n" +
            "{\n" +
            "  \"categoryScores\": {\n" + categoryScoreSchema + "\n  },\n" +
            "  \"communication\": {\"score\": 1-5, \"rationale\": \"\", \"strengths\": [], \"weaknesses\": []},\n" +
            "  \"proposedVerdict\": \"\",\n" +
            "  \"summary\": \"2-3 sentences for manager\",\n" +
            "  \"resumeConsistency\": {\n" +
            "    \"claimed\": [], \"demonstrated\": [], \"notDemonstrated\": [],\n" +
            "    \"consistencyScore\": 1-5, \"flags\": []\n" +
            "  },\n" +
            "  \"behavioralSignals\": {\n" +
            "    \"ownershipLevel\": \"low|medium|high\",\n" +
            "    \"learningAgility\": \"low|medium|high\",\n" +
            "    \"communicationStructure\": \"low|medium|high\",\n" +
            "    \"confidenceCalibration\": \"low|medium|high\",\n" +
            "    \"summary\": \"\"\n" +
            "  },\n" +
            "  \"interviewQuality\": {\n" +
            "    \"coverageScore\": 1-5,\n" +
            "    \"categoriesCovered\": [],\n" +
            "    \"categoriesMissed\": [],\n" +
            "    \"note\": \"\"\n" +
            "  },\n" +
            "  \"candidateFeedback\": {\n" +
            "    \"overallSummary\": \"plain English 2-3 sentences for candidate\",\n" +
            "    \"prosAndCons\": [\n" +
            "      {\n" +
            "        \"category\": \"category label\",\n" +
            "        \"pros\": [\"specific thing you did well\"],\n" +
            "        \"cons\": [\"specific thing to improve\"]\n" +
            "      }\n" +
            "    ],\n" +
            "    \"resumeConsistencyForCandidate\": [\n" +
            "      {\"claim\": \"skill from resume\", \"demonstrated\": true/false, \"note\": \"brief explanation\"}\n" +
            "    ],\n" +
            "    \"roadmap\": [\n" +
            "      {\n" +
            "        \"day\": \"Day 1\",\n" +
            "        \"category\": \"category label\",\n" +
            "        \"gap\": \"specific gap\",\n" +
            "        \"focus\": \"exact topic to study\",\n" +
            "        \"whyItMatters\": \"why this gap matters for the role\",\n" +
            "        \"resource\": \"resource name\",\n" +
            "        \"resourceUrl\": \"https://actual-url.com\",\n" +
            "        \"exercise\": \"hands-on task\",\n" +
            "        \"estimatedHours\": 2\n" +
            "      }\n" +
            "    ],\n" +
            "    \"estimatedReadinessTimeline\": \"\"\n" +
            "  }\n" +
            "}";

        String user = "Role: " + req.getJdTitle() + "\n" +
            "JD:\n" + req.getJdText().substring(0, Math.min(500, req.getJdText().length())) + "\n" +
            "Resume (claimed skills):\n" + (req.getResumeSummary() != null
                ? req.getResumeSummary().substring(0, Math.min(500, req.getResumeSummary().length())) : "") + "\n" +
            "Claimed expertise from profile: " + claimed + "\n" +
            "\nExtracted evidence per category:\n" + evidenceSummary;

        String raw = openAiClient.chatAssessment(system, user);
        JsonNode json = objectMapper.readTree(raw);
        return buildResult(json, categories, evidence);
    }

    private Map<String, Object> buildResult(JsonNode json, List<Map<String, Object>> categories,
                                             Map<String, List<String>> evidence) {
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
        result.put("resumeConsistency", parseObject(json.path("resumeConsistency")));
        result.put("behavioralSignals", parseObject(json.path("behavioralSignals")));
        result.put("interviewQuality", parseObject(json.path("interviewQuality")));
        result.put("candidateFeedback", parseCandidateFeedback(json.path("candidateFeedback")));
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

        // prosAndCons
        List<Map<String, Object>> prosAndCons = new ArrayList<>();
        node.path("prosAndCons").forEach(item -> prosAndCons.add(Map.of(
            "category", item.path("category").asText(""),
            "pros", toStringList(item.path("pros")),
            "cons", toStringList(item.path("cons"))
        )));

        // resumeConsistencyForCandidate
        List<Map<String, Object>> resumeConsistency = new ArrayList<>();
        node.path("resumeConsistencyForCandidate").forEach(item -> resumeConsistency.add(Map.of(
            "claim", item.path("claim").asText(""),
            "demonstrated", item.path("demonstrated").asBoolean(false),
            "note", item.path("note").asText("")
        )));

        // roadmap
        List<Map<String, Object>> roadmap = new ArrayList<>();
        node.path("roadmap").forEach(day -> roadmap.add(Map.of(
            "day", day.path("day").asText(""),
            "category", day.path("category").asText(""),
            "gap", day.path("gap").asText(""),
            "focus", day.path("focus").asText(""),
            "whyItMatters", day.path("whyItMatters").asText(""),
            "resource", day.path("resource").asText(""),
            "resourceUrl", day.path("resourceUrl").asText(""),
            "exercise", day.path("exercise").asText(""),
            "estimatedHours", day.path("estimatedHours").asInt(2)
        )));

        return Map.of(
            "overallSummary", node.path("overallSummary").asText(""),
            "prosAndCons", prosAndCons,
            "resumeConsistencyForCandidate", resumeConsistency,
            "roadmap", roadmap,
            "estimatedReadinessTimeline", node.path("estimatedReadinessTimeline").asText("")
        );
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
            "overallSummary", "Not enough responses were recorded to generate feedback.",
            "prosAndCons", List.of(),
            "resumeConsistencyForCandidate", List.of(),
            "roadmap", List.of(),
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
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("categoryScores", List.of(
            Map.of("dimension", "coreJava", "value", techScore, "rationale", "Heuristic only.",
                "evidence", "", "gap", "", "strengths", "[]", "weaknesses", "[]", "confidence", "low"),
            Map.of("dimension", "communication", "value", commScore, "rationale", "Heuristic only.",
                "evidence", "", "gap", "", "strengths", "[]", "weaknesses", "[]", "confidence", "low")
        ));
        result.put("proposedVerdict", "NEEDS_1_WEEK_PREP");
        result.put("summary", "Heuristic assessment — configure Claude API key for real scoring.");
        result.put("candidateFeedback", Map.of(
            "overallSummary", "AI assessment not available.",
            "prosAndCons", List.of(),
            "resumeConsistencyForCandidate", List.of(),
            "roadmap", List.of(),
            "estimatedReadinessTimeline", "Unknown"
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
        
        Set<String> set1 = Set.of(words1);
        Set<String> set2 = Set.of(words2);
        
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);
        
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
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
}
