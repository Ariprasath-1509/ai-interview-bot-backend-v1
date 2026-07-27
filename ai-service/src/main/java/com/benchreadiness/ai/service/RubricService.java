package com.benchreadiness.ai.service;

import com.benchreadiness.ai.dto.RubricRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RubricService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RubricService.class);

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public RubricService(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    @Cacheable(value = "rubrics", key = "#req.jdTitle + '_' + T(java.util.Objects).hash(#req.jdText, #req.resumeSummary)")
    public Map<String, Object> generateRubric(RubricRequest req, String userId) {
        log.info("generateRubric called for JD: {} (will use cache if available)", req.getJdTitle());
        if (!llmClient.isConfigured()) return fallbackRubric(req);
        try {
            log.info("Cache MISS - Generating new rubric via Claude for JD: {}", req.getJdTitle());
            return llmRubric(req, userId);
        } catch (Exception e) {
            log.warn("Rubric generation failed: {}", e.getMessage());
            return fallbackRubric(req);
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
            "JD:\n" + req.getJdText().substring(0, Math.min(3000, req.getJdText().length())) + "\n" +
            "Resume:\n" + req.getResumeSummary().substring(0, Math.min(2000, req.getResumeSummary().length())) +
            (req.getFocusAreas() != null && !req.getFocusAreas().isBlank()
                ? "\nFocus areas: " + req.getFocusAreas() : "");

        String raw = llmClient.chatRubricWithTracking(system, user, req.getInterviewId(), userId);
        JsonNode json = objectMapper.readTree(JsonRepairUtil.repair(raw));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rubric", objectMapper.convertValue(json.path("rubric"), Object.class));
        result.put("candidateProfile", objectMapper.convertValue(json.path("candidateProfile"), Object.class));
        return result;
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
