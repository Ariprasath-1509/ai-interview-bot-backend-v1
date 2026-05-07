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
        if (!llmClient.isConfigured()) return fallbackRubric(req);
        try {
            return llmRubric(req, userId);
        } catch (Exception e) {
            log.warn("Rubric generation failed: {}", e.getMessage());
            return fallbackRubric(req);
        }
    }

    private Map<String, Object> llmRubric(RubricRequest req, String userId) throws Exception {
        String system =
            "You are a technical hiring expert. Given a JD and candidate resume, return ONLY valid JSON:\n" +
            "{\n" +
            "  \"rubric\": {\n" +
            "    \"categories\": [\n" +
            "      {\"key\": \"camelCaseKey\", \"label\": \"Human Label\", \"description\": \"what to probe\", \"weight\": 1-3}\n" +
            "    ],\n" +
            "    \"focusAreas\": [\"area1\", \"area2\"]\n" +
            "  },\n" +
            "  \"candidateProfile\": {\n" +
            "    \"yearsOfExperience\": number,\n" +
            "    \"level\": \"junior|mid|senior|staff\",\n" +
            "    \"primarySkills\": [\"skill1\", \"skill2\"],\n" +
            "    \"claimedExpertise\": [\"area1\", \"area2\"],\n" +
            "    \"questionDifficulty\": \"easy|medium|hard\",\n" +
            "    \"resumeSummary\": \"1 sentence summary\"\n" +
            "  }\n" +
            "}\n" +
            "Rules:\n" +
            "- 4-6 categories max, derived from actual JD requirements\n" +
            "- weight: 3=core requirement, 2=important, 1=nice-to-have\n" +
            "- questionDifficulty based on YOE: <2=easy, 2-5=medium, >5=hard\n" +
            "- No markdown, no explanation, raw JSON only";

        String user = "JD Title: " + req.getJdTitle() + "\n" +
            "JD:\n" + req.getJdText().substring(0, Math.min(600, req.getJdText().length())) + "\n" +
            "Resume:\n" + req.getResumeSummary().substring(0, Math.min(500, req.getResumeSummary().length())) +
            (req.getFocusAreas() != null && !req.getFocusAreas().isBlank()
                ? "\nFocus areas: " + req.getFocusAreas() : "");

        String raw = llmClient.chatRubricWithTracking(system, user, req.getInterviewId(), userId);
        JsonNode json = objectMapper.readTree(raw);

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
