package com.benchreadiness.ai.service;

import com.benchreadiness.ai.client.ComplianceServiceClient;
import com.benchreadiness.ai.dto.RubricRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RubricService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RubricService.class);

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final ComplianceServiceClient complianceServiceClient;

    // F4: in-memory layer (survives within a pod lifetime, evicted on restart)
    private final ConcurrentHashMap<String, Map<String, Object>> inMemoryRubricCache = new ConcurrentHashMap<>();

    public RubricService(LlmClient llmClient, ObjectMapper objectMapper,
                         ComplianceServiceClient complianceServiceClient) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.complianceServiceClient = complianceServiceClient;
    }

    // F4: no @Cacheable — replaced with two-tier (in-memory + DB) manual cache
    public Map<String, Object> generateRubric(RubricRequest req, String userId) {
        String cacheKey = buildRubricCacheKey(req);

        // 1. in-memory check (fastest)
        Map<String, Object> cached = inMemoryRubricCache.get(cacheKey);
        if (cached != null) {
            log.info("generateRubric: in-memory HIT for key {}", cacheKey);
            return cached;
        }

        // 2. DB check (survives restarts)
        try {
            Map<String, Object> dbEntry = complianceServiceClient.getRubricCache(cacheKey);
            if (dbEntry != null && dbEntry.containsKey("rubricJson")) {
                String rubricJson = (String) dbEntry.get("rubricJson");
                @SuppressWarnings("unchecked")
                Map<String, Object> result = objectMapper.readValue(rubricJson, Map.class);
                inMemoryRubricCache.put(cacheKey, result);
                log.info("generateRubric: DB HIT for key {}", cacheKey);
                return result;
            }
        } catch (Exception e) {
            log.warn("generateRubric: DB cache lookup failed ({}); will call Claude", e.getMessage());
        }

        // 3. Generate — deterministic from a screening checklist when the client has one, else Claude
        boolean hasChecklist = req.getScreeningChecklistJson() != null && !req.getScreeningChecklistJson().isBlank();
        if (hasChecklist) {
            try {
                Map<String, Object> result = checklistRubric(req, userId);
                try {
                    String json = objectMapper.writeValueAsString(result);
                    complianceServiceClient.storeRubricCache(Map.of("cacheKey", cacheKey, "rubricJson", json));
                } catch (Exception e) {
                    log.warn("generateRubric: failed to persist checklist rubric to DB ({})", e.getMessage());
                }
                inMemoryRubricCache.put(cacheKey, result);
                return result;
            } catch (Exception e) {
                log.warn("Checklist-based rubric generation failed, falling back to JD-guessed rubric: {}", e.getMessage());
                // fall through to the normal LLM-guessed path below
            }
        }

        log.info("generateRubric: CACHE MISS — calling Claude for JD: {}", req.getJdTitle());
        if (!llmClient.isConfigured()) return fallbackRubric(req);
        try {
            Map<String, Object> result = llmRubric(req, userId);

            // 4. Store in DB then in-memory
            try {
                String json = objectMapper.writeValueAsString(result);
                complianceServiceClient.storeRubricCache(Map.of("cacheKey", cacheKey, "rubricJson", json));
            } catch (Exception e) {
                log.warn("generateRubric: failed to persist rubric to DB ({})", e.getMessage());
            }
            inMemoryRubricCache.put(cacheKey, result);
            return result;
        } catch (Exception e) {
            log.warn("Rubric generation failed: {}", e.getMessage());
            return fallbackRubric(req);
        }
    }

    private String buildRubricCacheKey(RubricRequest req) {
        String raw = req.getJdTitle() + "|" + req.getJdText() + "|" + req.getResumeSummary()
            + "|" + (req.getScreeningChecklistJson() != null ? req.getScreeningChecklistJson() : "");
        return sha256(raw);
    }

    private String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            // fallback: simple hashCode as hex so cache still works
            return Integer.toHexString(value.hashCode());
        }
    }

    private Map<String, Object> llmRubric(RubricRequest req, String userId) throws Exception {
        String system =
            "You are a master technical hiring architect. Analyze the provided Job Description (JD) and Candidate Resume to construct a granular, high-fidelity assessment framework.\n" +
            "\n" +
            "CORE CONSTRAINTS:\n" +
            "1. Extract a maximum of 4-6 distinct evaluation categories derived directly from explicit JD text.\n" +
            "2. Map weights strictly: 3 = Core absolute requirement (MUST_HAVE), 2 = Important (GOOD_TO_HAVE), 1 = Nice-to-have (GOOD_TO_HAVE).\n" +
            "3. Set questionDifficulty dynamically using Years of Experience (YOE): <2 years = easy, 2-5 years = medium, >5 years = hard.\n" +
            "4. Each category MUST include a client-facing subSkill label (e.g. Java Fundamentals, Syntax & Queries).\n" +
            "5. Each category MUST include exactly 4 proficiencyOptions from strongest to weakest tier.\n" +
            "6. Optional note field for skill substitution context (e.g. MySQL tested instead of generic SQL).\n" +
            "\n" +
            "OUTPUT PROTOCAL:\n" +
            "Your response must consist of your internal reasoning steps, followed directly by the final raw JSON payload. Ensure your JSON perfectly mirrors this schema layout with no trailing elements:\n" +
            "{\n" +
            "  \"rubric\": {\n" +
            "    \"categories\": [\n" +
            "      {\n" +
            "        \"key\": \"camelCaseKey\",\n" +
            "        \"label\": \"Human Label\",\n" +
            "        \"subSkill\": \"Specific sub-skill label for client report\",\n" +
            "        \"description\": \"What exact mechanism to probe\",\n" +
            "        \"weight\": 3,\n" +
            "        \"priority\": \"MUST_HAVE\",\n" +
            "        \"note\": \"Optional client note or empty string\",\n" +
            "        \"proficiencyOptions\": [\n" +
            "          \"Strong knowledge of ...\",\n" +
            "          \"Good knowledge of ...\",\n" +
            "          \"Only theoretical knowledge of ... with no practical experience\",\n" +
            "          \"No knowledge of ...\"\n" +
            "        ]\n" +
            "      }\n" +
            "    ],\n" +
            "    \"focusAreas\": [\"area1\", \"area2\"]\n" +
            "  },\n" +
            "  \"candidateProfile\": {\n" +
            "    \"yearsOfExperience\": 5,\n" +
            "    \"level\": \"mid\",\n" +
            "    \"primarySkills\": [\"skill1\"],\n" +
            "    \"claimedExpertise\": [\"area1\"],\n" +
            "    \"questionDifficulty\": \"medium\",\n" +
            "    \"resumeSummary\": \"A concise one-sentence description\"\n" +
            "  }\n" +
            "}\n" +
            "\n" +
            "Valid values — level: junior | mid | senior | staff. questionDifficulty: easy (<2 yrs) | medium (2-5 yrs) | hard (>5 yrs).";

        String user = "JD Title: " + req.getJdTitle() + "\n" +
            "JD:\n" + req.getJdText().substring(0, Math.min(6000, req.getJdText().length())) + "\n" +
            "Resume:\n" + req.getResumeSummary().substring(0, Math.min(4000, req.getResumeSummary().length())) +
            (req.getFocusAreas() != null && !req.getFocusAreas().isBlank()
                ? "\nFocus areas: " + req.getFocusAreas() : "");

        String raw = llmClient.chatRubricWithTracking(system, user, req.getInterviewId(), userId);
        JsonNode json = objectMapper.readTree(JsonRepairUtil.repair(raw));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rubric", objectMapper.convertValue(json.path("rubric"), Object.class));
        result.put("candidateProfile", objectMapper.convertValue(json.path("candidateProfile"), Object.class));
        return result;
    }

    /**
     * Builds rubric categories deterministically from a client's screening checklist — no LLM
     * guessing of dimensions or weights, so the interview and scoring engine stick exactly to
     * what the client specified. The LLM is only used for candidateProfile (resume-derived),
     * which the checklist has no way to provide.
     */
    private Map<String, Object> checklistRubric(RubricRequest req, String userId) throws Exception {
        JsonNode checklist = objectMapper.readTree(req.getScreeningChecklistJson());
        JsonNode dimensions = checklist.path("dimensions");

        List<Map<String, Object>> categories = new ArrayList<>();
        if (dimensions.isArray()) {
            for (JsonNode dim : dimensions) {
                String key = dim.path("key").asText();
                String label = dim.path("label").asText();
                String goodLooksLike = dim.path("goodLooksLike").asText("");
                double weightPct = dim.path("weightPct").asDouble(0);
                categories.add(checklistCategory(key, label, goodLooksLike, weightPct));
            }
        }

        Map<String, Object> screeningChecklist = new LinkedHashMap<>();
        screeningChecklist.put("proceedGates", toStringList(checklist.path("proceedGates")));
        screeningChecklist.put("rejectSignals", toStringList(checklist.path("rejectSignals")));
        screeningChecklist.put("validateFlags", toStringList(checklist.path("validateFlags")));
        screeningChecklist.put("knockoutQuestions", toStringList(checklist.path("knockoutQuestions")));
        screeningChecklist.put("recommendationBands", objectMapper.convertValue(checklist.path("recommendationBands"), Object.class));
        screeningChecklist.put("ratingScale", objectMapper.convertValue(resolveRatingScale(checklist.path("ratingScale")), Object.class));

        Map<String, Object> rubric = new LinkedHashMap<>();
        rubric.put("categories", categories);
        rubric.put("focusAreas", List.of());
        rubric.put("screeningChecklist", screeningChecklist);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rubric", rubric);
        result.put("candidateProfile", llmCandidateProfileOnly(req, userId));
        return result;
    }

    /** Falls back to a generic 1-5 scale when the source checklist did not define its own rating scale. */
    private Map<String, Object> resolveRatingScale(JsonNode ratingScaleNode) {
        Map<String, Object> scale = new LinkedHashMap<>();
        if (ratingScaleNode != null && ratingScaleNode.isObject()
                && ratingScaleNode.has("levels") && ratingScaleNode.path("levels").isArray()
                && ratingScaleNode.path("levels").size() > 0) {
            scale.put("min", ratingScaleNode.path("min").asInt(1));
            scale.put("max", ratingScaleNode.path("max").asInt(5));
            List<Map<String, Object>> levels = new ArrayList<>();
            for (JsonNode level : ratingScaleNode.path("levels")) {
                Map<String, Object> l = new LinkedHashMap<>();
                l.put("score", level.path("score").asInt());
                l.put("definition", level.path("definition").asText(""));
                levels.add(l);
            }
            scale.put("levels", levels);
            return scale;
        }
        scale.put("min", 1);
        scale.put("max", 5);
        scale.put("levels", List.of(
            Map.of("score", 1, "definition", "No evidence or a fundamental gap in this area"),
            Map.of("score", 2, "definition", "Basic or theoretical knowledge only; no meaningful production experience"),
            Map.of("score", 3, "definition", "Adequate hands-on experience; meets the baseline requirement"),
            Map.of("score", 4, "definition", "Strong hands-on experience; meets the requirement with clear, specific examples"),
            Map.of("score", 5, "definition", "Expert-level; exceeds the requirement and could mentor others in this area")
        ));
        return scale;
    }

    /** Lighter LLM call used in checklist mode — resume/JD-derived candidate profile only, no rubric guessing. */
    private Map<String, Object> llmCandidateProfileOnly(RubricRequest req, String userId) throws Exception {
        if (req.getResumeSummary() == null || req.getResumeSummary().isBlank() || !llmClient.isConfigured()) {
            return Map.of(
                "yearsOfExperience", 0,
                "level", "mid",
                "primarySkills", List.of(),
                "claimedExpertise", List.of(),
                "questionDifficulty", "medium",
                "resumeSummary", ""
            );
        }
        String system =
            "Extract a candidate profile from the resume against the role. Output ONLY this JSON, no markdown, no prose:\n" +
            "{\n" +
            "  \"yearsOfExperience\": 5,\n" +
            "  \"level\": \"junior|mid|senior|staff\",\n" +
            "  \"primarySkills\": [\"skill1\"],\n" +
            "  \"claimedExpertise\": [\"area1\"],\n" +
            "  \"questionDifficulty\": \"easy (<2 yrs) | medium (2-5 yrs) | hard (>5 yrs)\",\n" +
            "  \"resumeSummary\": \"A concise one-sentence description\"\n" +
            "}";
        String user = "Role: " + req.getJdTitle() + "\n" +
            "Resume:\n" + req.getResumeSummary().substring(0, Math.min(4000, req.getResumeSummary().length()));

        String raw = llmClient.chatRubricWithTracking(system, user, req.getInterviewId(), userId);
        JsonNode json = objectMapper.readTree(JsonRepairUtil.repair(raw));
        return objectMapper.convertValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
    }

    private List<String> toStringList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node.isArray()) node.forEach(n -> list.add(n.asText()));
        return list;
    }

    /** weightPct >= 20 is treated as a MUST_HAVE-tier dimension for the legacy 1-3 weight field
     *  (still consumed by the default scoring engine); the exact weightPct is preserved for the
     *  checklist-aware weighted scoring path. */
    private Map<String, Object> checklistCategory(String key, String label, String goodLooksLike, double weightPct) {
        int legacyWeight = weightPct >= 20 ? 3 : weightPct >= 10 ? 2 : 1;
        String priority = legacyWeight == 3 ? "MUST_HAVE" : "GOOD_TO_HAVE";
        Map<String, Object> cat = new LinkedHashMap<>();
        cat.put("key", key);
        cat.put("label", label);
        cat.put("subSkill", label);
        cat.put("description", goodLooksLike);
        cat.put("weight", legacyWeight);
        cat.put("weightPct", weightPct);
        cat.put("priority", priority);
        cat.put("note", "");
        cat.put("proficiencyOptions", List.of(
            "Strong knowledge of " + label,
            "Good knowledge of " + label,
            "Only theoretical knowledge of " + label + " with no practical experience",
            "No knowledge of " + label
        ));
        return cat;
    }

    private Map<String, Object> fallbackRubric(RubricRequest req) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rubric", Map.of(
            "categories", List.of(
                category("coreJava", "Java", "Java Fundamentals", "OOP, collections, memory, threads", 3, "MUST_HAVE", ""),
                category("spring", "Spring Boot", "Spring Boot Fundamentals", "IoC, REST, data, security", 3, "MUST_HAVE", ""),
                category("microservices", "Software Architecture", "Microservices Architecture", "Service design, communication, resilience", 2, "GOOD_TO_HAVE", ""),
                category("miscellaneous", "Problem Solving", "Case Study Problem-Solving", "SQL, Docker, system design", 1, "GOOD_TO_HAVE",
                    "For SQL skills, candidates are typically assessed on MySQL when applicable.")
            ),
            "focusAreas", List.of()
        ));
        result.put("candidateProfile", Map.of(
            "yearsOfExperience", 0,
            "level", "mid",
            "primarySkills", List.of(),
            "claimedExpertise", List.of(),
            "questionDifficulty", "medium",
            "resumeSummary", ""
        ));
        return result;
    }

    private Map<String, Object> category(String key, String label, String subSkill, String description,
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
}
