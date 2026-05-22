package com.benchreadiness.ai.service;

import com.benchreadiness.ai.dto.RubricRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class RubricService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RubricService.class);

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RubricService(LlmClient llmClient) {
        this.llmClient = llmClient;
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
            "2. Map weights strictly: 3 = Core absolute requirement, 2 = Important architectural/system knowledge, 1 = Nice-to-have or peripheral tools.\n" +
            "3. Set questionDifficulty dynamically using Years of Experience (YOE): <2 years = easy, 2-5 years = medium, >5 years = hard.\n" +
            "\n" +
            "OUTPUT PROTOCAL:\n" +
            "Your response must consist of your internal reasoning steps, followed directly by the final raw JSON payload. Ensure your JSON perfectly mirrors this schema layout with no trailing elements:\n" +
            "{\n" +
            "  \"rubric\": {\n" +
            "    \"categories\": [\n" +
            "      {\"key\": \"camelCaseKey\", \"label\": \"Human Label\", \"description\": \"What exact mechanism to probe\", \"weight\": 1}\n" +
            "    ],\n" +
            "    \"focusAreas\": [\"area1\", \"area2\"]\n" +
            "  },\n" +
            "  \"candidateProfile\": {\n" +
            "    \"yearsOfExperience\": 0,\n" +
            "    \"level\": \"junior\", \"mid\", \"senior\", or \"staff\",\n" +
            "    \"primarySkills\": [\"skill1\"],\n" +
            "    \"claimedExpertise\": [\"area1\"],\n" +
            "    \"questionDifficulty\": \"easy\", \"medium\", or \"hard\",\n" +
            "    \"resumeSummary\": \"A concise one-sentence description\"\n" +
            "  }\n" +
            "}";

        String user = "JD Title: " + req.getJdTitle() + "\n" +
            "JD:\n" + req.getJdText().substring(0, Math.min(600, req.getJdText().length())) + "\n" +
            "Resume:\n" + req.getResumeSummary().substring(0, Math.min(500, req.getResumeSummary().length())) +
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
        // Default Java backend rubric when AI not available
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rubric", Map.of(
            "categories", java.util.List.of(
                Map.of("key", "coreJava", "label", "Core Java", "description", "OOP, collections, memory, threads", "weight", 3),
                Map.of("key", "spring", "label", "Spring/Spring Boot", "description", "IoC, REST, data, security", "weight", 3),
                Map.of("key", "microservices", "label", "Microservices", "description", "Service design, communication, resilience", "weight", 2),
                Map.of("key", "miscellaneous", "label", "Miscellaneous", "description", "SQL, Docker, system design", "weight", 1)
            ),
            "focusAreas", java.util.List.of()
        ));
        result.put("candidateProfile", Map.of(
            "yearsOfExperience", 0,
            "level", "mid",
            "primarySkills", java.util.List.of(),
            "claimedExpertise", java.util.List.of(),
            "questionDifficulty", "medium",
            "resumeSummary", ""
        ));
        return result;
    }
}
