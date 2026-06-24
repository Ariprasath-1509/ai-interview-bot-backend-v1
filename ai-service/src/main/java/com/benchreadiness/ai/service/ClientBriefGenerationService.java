package com.benchreadiness.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ClientBriefGenerationService {

    private static final org.slf4j.Logger log =
        org.slf4j.LoggerFactory.getLogger(ClientBriefGenerationService.class);

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public ClientBriefGenerationService(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> generateStructuredBrief(Map<String, Object> request, String userId) throws Exception {
        if (!llmClient.isConfigured()) {
            throw new IllegalStateException("LLM provider not configured");
        }

        String interviewId = stringVal(request.get("interviewId"));
        String candidateName = stringVal(request.get("candidateName"));
        if (candidateName.isBlank()) candidateName = "Candidate";
        String assessmentJson = stringVal(request.get("assessmentJson"));
        if (assessmentJson.isBlank()) {
            throw new IllegalArgumentException("No assessment available for this interview");
        }

        Map<String, Object> assessment = objectMapper.readValue(assessmentJson, Map.class);
        List<Map<String, Object>> rubricCategories = parseCategoriesFromRequest(request);
        Map<String, Integer> scoresByKey = extractScores(assessment);
        log.info("Client brief {} normalized scores (1-10 display): {}", interviewId, scoresByKey);
        Map<String, Map<String, Object>> scoreDetails = extractScoreDetails(assessment);
        List<Map<String, Object>> categories = alignCategoriesWithScores(
            rubricCategories, scoresByKey, scoreDetails);
        List<Map<String, Object>> categoriesForLlm = categoriesForBriefGeneration(categories, scoresByKey);

        log.info("Client brief {}: rubricCategories={}, scoreDimensions={}, alignedCategories={}",
            interviewId, rubricCategories.size(), scoresByKey.size(), categories.size());

        String transcriptJson = stringVal(request.get("transcriptJson"));
        List<Map<String, Object>> questionInputs = parseQuestionInputs(request.get("questions"));
        List<Map<String, Object>> taggedQuestions = tagQuestionsWithSkills(
            questionInputs, categoriesForLlm, interviewId, userId);

        Map<String, Object> llmContent = generateBriefContent(
            candidateName,
            stringVal(request.get("jdTitle")),
            stringVal(request.get("jdText")),
            stringVal(request.get("clientName")),
            stringVal(request.get("seniorityBand")),
            categoriesForLlm,
            scoresByKey,
            scoreDetails,
            assessment,
            transcriptJson,
            questionInputs,
            interviewId,
            userId
        );

        List<Map<String, Object>> mustHave = new ArrayList<>();
        List<Map<String, Object>> goodToHave = new ArrayList<>();
        List<Map<String, Object>> skillAssessments = new ArrayList<>();
        int mustRank = 1;
        int goodRank = 1;
        int assessRank = 1;

        Map<String, Map<String, Object>> assessmentsByKey = mapByCategoryKey(
            (List<Map<String, Object>>) llmContent.getOrDefault("skillAssessments", List.of()));

        for (Map<String, Object> cat : categories) {
            String key = stringVal(cat.get("key"));
            if (key.isBlank() || "communication".equals(key)) continue;
            Integer score = scoresByKey.get(key);
            if (score == null) continue;

            String priority = stringVal(cat.get("priority"));
            if (priority.isBlank()) {
                int weight = parseInt(cat.get("weight"), 2);
                priority = weight >= 3 ? "MUST_HAVE" : "GOOD_TO_HAVE";
            }

            Map<String, Object> summary = skillSummary(
                "MUST_HAVE".equals(priority) ? mustRank++ : goodRank++,
                cat, score);

            Map<String, Object> assessmentEntry = assessmentsByKey.getOrDefault(key, Map.of());
            Map<String, Object> detailed = buildSkillAssessment(
                assessRank++, cat, score, assessmentEntry, scoreDetails.get(key), candidateName);

            if ("MUST_HAVE".equals(priority)) {
                mustHave.add(summary);
            } else {
                goodToHave.add(summary);
            }
            skillAssessments.add(detailed);
        }

        Integer commScore = scoresByKey.get("communication");
        if (commScore != null) {
            Map<String, Object> commCat = buildCommunicationCategory();
            goodToHave.add(skillSummary(goodRank++, commCat, commScore));
            Map<String, Object> commAssessment = assessmentsByKey.getOrDefault("communication", Map.of());
            skillAssessments.add(buildSkillAssessment(
                assessRank++, commCat, commScore, commAssessment, scoreDetails.get("communication"), candidateName));
        }

        Map<String, Object> header = buildHeader(request);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("header", header);
        result.put("mustHaveSkills", mustHave);
        result.put("goodToHaveSkills", goodToHave);
        result.put("questionsAsked", taggedQuestions);
        result.put("overallFeedback", stringVal(llmContent.get("overallFeedback")));
        result.put("skillAssessments", skillAssessments);
        result.put("source", "ai");
        applyEmptyAssessmentFallback(result, assessment, interviewId);
        return result;
    }

    private List<Map<String, Object>> alignCategoriesWithScores(
            List<Map<String, Object>> rubricCategories,
            Map<String, Integer> scoresByKey,
            Map<String, Map<String, Object>> scoreDetails) {
        Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
        for (Map<String, Object> cat : rubricCategories) {
            String key = stringVal(cat.get("key"));
            if (!key.isBlank()) {
                byKey.put(key, cat);
            }
        }

        for (Map.Entry<String, Integer> entry : scoresByKey.entrySet()) {
            String key = entry.getKey();
            if (key.isBlank() || "communication".equals(key)) continue;
            if (!byKey.containsKey(key)) {
                byKey.put(key, categoryFromScoreDimension(key, scoreDetails.get(key)));
            }
        }

        List<Map<String, Object>> aligned = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : scoresByKey.entrySet()) {
            String key = entry.getKey();
            if (key.isBlank() || "communication".equals(key)) continue;
            Map<String, Object> cat = byKey.get(key);
            if (cat != null) {
                aligned.add(cat);
            }
        }
        return aligned;
    }

    private List<Map<String, Object>> categoriesForBriefGeneration(
            List<Map<String, Object>> alignedTechnical, Map<String, Integer> scoresByKey) {
        List<Map<String, Object>> all = new ArrayList<>(alignedTechnical);
        if (scoresByKey.containsKey("communication")) {
            all.add(buildCommunicationCategory());
        }
        return all;
    }

    private Map<String, Object> buildCommunicationCategory() {
        Map<String, Object> commCat = new LinkedHashMap<>();
        commCat.put("key", "communication");
        commCat.put("label", "Communication & Articulation");
        commCat.put("subSkill", "Verbal clarity, structure, and explanation quality");
        commCat.put("priority", "GOOD_TO_HAVE");
        commCat.put("weight", 2);
        commCat.put("note", "");
        commCat.put("proficiencyOptions", List.of(
            "Strong communication — clear, structured, and confident explanations with relevant examples",
            "Good communication — generally clear explanations with occasional lack of depth or structure",
            "Basic communication — understands questions but struggles to articulate reasoning or trade-offs clearly",
            "Weak communication — vague, unstructured, or incomplete verbal responses"
        ));
        return commCat;
    }

    private Map<String, Object> categoryFromScoreDimension(String key, Map<String, Object> detail) {
        String label = formatDimensionLabel(key);
        Map<String, Object> cat = new LinkedHashMap<>();
        cat.put("key", key);
        cat.put("label", label);
        cat.put("subSkill", label);
        cat.put("description", detail != null ? stringVal(detail.get("rationale")) : label);
        cat.put("weight", 2);
        cat.put("priority", "GOOD_TO_HAVE");
        cat.put("note", "");
        cat.put("proficiencyOptions", defaultProficiencyOptions(label));
        return cat;
    }

    private String formatDimensionLabel(String key) {
        if (key == null || key.isBlank()) return "Skill";
        String spaced = key.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ');
        if (spaced.length() > 0) {
            return spaced.substring(0, 1).toUpperCase() + spaced.substring(1);
        }
        return key;
    }

    private void applyEmptyAssessmentFallback(
            Map<String, Object> result, Map<String, Object> assessment, String interviewId) {
        List<?> must = (List<?>) result.getOrDefault("mustHaveSkills", List.of());
        List<?> good = (List<?>) result.getOrDefault("goodToHaveSkills", List.of());
        if (!must.isEmpty() || !good.isEmpty()) {
            return;
        }

        log.warn("Client brief {} has no skill sections — assessment categoryScores missing or keys mismatched",
            interviewId);

        String summary = stringVal(assessment.get("summary"));
        String verdict = stringVal(assessment.get("proposedVerdict"));
        String feedback = stringVal(result.get("overallFeedback"));

        if (feedback.isBlank()) {
            if (!summary.isBlank()) {
                result.put("overallFeedback", summary);
            } else {
                result.put("overallFeedback",
                    "No per-skill scores are available for this interview. "
                        + "Complete the interview with sufficient candidate responses, run AI assessment, "
                        + "then regenerate this client brief.");
            }
        }

        result.put("generationWarning",
            "No category scores found in assessment (source: " + stringVal(assessment.get("source"))
                + ", verdict: " + verdict + "). "
                + "Re-run AI assessment with a completed transcript, then generate the brief again.");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseCategoriesFromRequest(Map<String, Object> request) throws Exception {
        String rubricJson = stringVal(request.get("rubricJson"));
        if (!rubricJson.isBlank()) {
            JsonNode node = objectMapper.readTree(rubricJson);
            JsonNode cats = node.path("categories");
            if (!cats.isArray() || cats.isEmpty()) {
                cats = node.path("rubric").path("categories");
            }
            if (cats.isArray() && !cats.isEmpty()) {
                List<Map<String, Object>> list = new ArrayList<>();
                cats.forEach(c -> list.add(categoryFromNode(c)));
                return list;
            }
        }
        Map<String, Object> assessment = objectMapper.readValue(
            stringVal(request.get("assessmentJson")), Map.class);
        List<Map<String, Object>> fromScores = new ArrayList<>();
        Object rowsObj = assessment.get("categoryScores");
        if (rowsObj instanceof List<?> rows) {
            for (Object rowObj : rows) {
                if (!(rowObj instanceof Map<?, ?> row)) continue;
                String dim = stringVal(row.get("dimension"));
                if (dim.isBlank() || "communication".equals(dim)) continue;
                Map<String, Object> cat = new LinkedHashMap<>();
                cat.put("key", dim);
                cat.put("label", dim);
                cat.put("subSkill", dim);
                cat.put("description", stringVal(row.get("rationale")));
                cat.put("weight", 2);
                cat.put("priority", "GOOD_TO_HAVE");
                cat.put("note", "");
                cat.put("proficiencyOptions", defaultProficiencyOptions(dim));
                fromScores.add(cat);
            }
        }
        return fromScores.isEmpty() ? defaultCategories() : fromScores;
    }

    private Map<String, Object> categoryFromNode(JsonNode c) {
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
        List<String> options = new ArrayList<>();
        JsonNode opts = c.path("proficiencyOptions");
        if (opts.isArray()) opts.forEach(n -> options.add(n.asText()));
        if (options.size() != 4) {
            options.clear();
            options.addAll(defaultProficiencyOptions(c.path("subSkill").asText(c.path("label").asText())));
        }
        cat.put("proficiencyOptions", options);
        return cat;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> extractScores(Map<String, Object> assessment) {
        Map<String, Integer> raw = new LinkedHashMap<>();
        for (Map<String, Object> row : collectCategoryScoreRows(assessment)) {
            String dim = stringVal(row.get("dimension"));
            Integer score = parseScoreValue(row.get("value"));
            if (!dim.isBlank() && score != null) {
                raw.put(dim, score);
            }
        }
        mergeTopLevelScore(assessment, raw, "communication");
        mergeTopLevelScore(assessment, raw, "technicalKnowledge");

        int scoreMax = resolveAssessmentScoreMax(assessment, raw);
        Map<String, Integer> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : raw.entrySet()) {
            normalized.put(entry.getKey(), toTenPointDisplayScore(entry.getValue(), scoreMax));
        }
        return normalized;
    }

    private int resolveAssessmentScoreMax(Map<String, Object> assessment, Map<String, Integer> scores) {
        Object explicit = assessment.get("scoreMax");
        if (explicit != null) {
            int max = parseInt(explicit, 10);
            return max > 0 ? max : 10;
        }
        if ("heuristic".equals(stringVal(assessment.get("source")))) {
            return 5;
        }
        if (scores.values().stream().anyMatch(v -> v > 5)) {
            return 10;
        }
        if (!scores.isEmpty() && scores.values().stream().allMatch(v -> v >= 1 && v <= 5)) {
            return "ollama-four-stage".equals(stringVal(assessment.get("source"))) ? 10 : 5;
        }
        return 10;
    }

    private int toTenPointDisplayScore(int rawScore, int scoreMax) {
        if (scoreMax <= 5) {
            return Math.min(10, Math.max(1, rawScore * 2));
        }
        return Math.min(10, Math.max(1, rawScore));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> extractScoreDetails(Map<String, Object> assessment) {
        Map<String, Map<String, Object>> details = new LinkedHashMap<>();
        for (Map<String, Object> row : collectCategoryScoreRows(assessment)) {
            String dim = stringVal(row.get("dimension"));
            if (dim.isBlank()) continue;
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("rationale", stringVal(row.get("rationale")));
            detail.put("gap", stringVal(row.get("gap")));
            detail.put("strengths", row.get("strengths"));
            detail.put("weaknesses", row.get("weaknesses"));
            detail.put("evidence", stringVal(row.get("evidence")));
            details.put(dim, detail);
        }
        mergeTopLevelScoreDetails(assessment, details, "communication");
        mergeTopLevelScoreDetails(assessment, details, "technicalKnowledge");
        return details;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> collectCategoryScoreRows(Map<String, Object> assessment) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Object rowsObj = assessment.get("categoryScores");
        if (rowsObj instanceof List<?> list) {
            for (Object rowObj : list) {
                if (!(rowObj instanceof Map<?, ?> row)) continue;
                Map<String, Object> normalized = normalizeScoreRow(stringVal(row.get("dimension")), row);
                if (normalized != null) rows.add(normalized);
            }
        } else if (rowsObj instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String dim = stringVal(entry.getKey());
                if (entry.getValue() instanceof Map<?, ?> row) {
                    Map<String, Object> normalized = normalizeScoreRow(dim, row);
                    if (normalized != null) rows.add(normalized);
                }
            }
        }
        return rows;
    }

  @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeScoreRow(String dimension, Map<?, ?> row) {
        if (dimension.isBlank()) return null;
        Integer score = parseScoreValue(row.get("value"));
        if (score == null) score = parseScoreValue(row.get("score"));
        if (score == null) return null;
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("dimension", dimension);
        normalized.put("value", score);
        normalized.put("rationale", stringVal(row.get("rationale")));
        normalized.put("gap", stringVal(row.get("gap")));
        normalized.put("strengths", row.get("strengths"));
        normalized.put("weaknesses", row.get("weaknesses"));
        normalized.put("evidence", stringVal(row.get("evidence")));
        return normalized;
    }

    private Integer parseScoreValue(Object value) {
        if (value == null) return null;
        try {
            double parsed = Double.parseDouble(String.valueOf(value));
            return (int) Math.round(parsed);
        } catch (Exception ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private void mergeTopLevelScore(Map<String, Object> assessment, Map<String, Integer> scores, String key) {
        Object obj = assessment.get(key);
        if (!(obj instanceof Map<?, ?> row)) return;
        Integer score = parseScoreValue(row.get("score"));
        if (score == null) score = parseScoreValue(row.get("value"));
        if (score != null) {
            scores.putIfAbsent(key, score);
        }
    }

    @SuppressWarnings("unchecked")
    private void mergeTopLevelScoreDetails(
            Map<String, Object> assessment, Map<String, Map<String, Object>> details, String key) {
        Object obj = assessment.get(key);
        if (!(obj instanceof Map<?, ?> row)) return;
        if (details.containsKey(key)) return;
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("rationale", stringVal(row.get("rationale")));
        detail.put("gap", stringVal(row.get("gap")));
        detail.put("strengths", row.get("strengths"));
        detail.put("weaknesses", row.get("weaknesses"));
        detail.put("evidence", stringVal(row.get("evidence")));
        details.put(key, detail);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseQuestionInputs(Object questionsObj) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!(questionsObj instanceof List<?> list)) return out;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            Map<String, Object> q = new LinkedHashMap<>();
            q.put("number", map.get("number"));
            q.put("text", stringVal(map.get("text")));
            q.put("difficulty", stringVal(map.get("difficulty")));
            q.put("type", stringVal(map.get("type")));
            out.add(q);
        }
        return out;
    }

    private List<Map<String, Object>> tagQuestionsWithSkills(
            List<Map<String, Object>> questions,
            List<Map<String, Object>> categories,
            String interviewId,
            String userId) {
        if (questions.isEmpty()) return List.of();
        if (categories.isEmpty()) {
            log.warn("Client brief {}: no rubric/score categories — listing questions without skill tags", interviewId);
            return untaggedQuestions(questions);
        }

        log.info("Client brief {}: heuristic skill tagging for {} questions", interviewId, questions.size());
        return tagQuestionsHeuristic(questions, categories);
    }

    private List<Map<String, Object>> tagQuestionsHeuristic(
            List<Map<String, Object>> questions,
            List<Map<String, Object>> categories) {
        Map<String, Map<String, Object>> categoryByKey = new LinkedHashMap<>();
        for (Map<String, Object> cat : categories) {
            String key = stringVal(cat.get("key"));
            if (!key.isBlank()) {
                categoryByKey.put(key, cat);
            }
        }
        Map<String, Object> communication = categoryByKey.getOrDefault(
            "communication", buildCommunicationCategory());

        List<Map<String, Object>> tagged = new ArrayList<>();
        for (Map<String, Object> q : questions) {
            int number = parseInt(q.get("number"), tagged.size() + 1);
            String text = stringVal(q.get("text"));
            String type = stringVal(q.get("type")).toUpperCase(Locale.ROOT);

            List<Map<String, Object>> mappings = new ArrayList<>();
            List<Map.Entry<Map<String, Object>, Integer>> scored = new ArrayList<>();
            for (Map<String, Object> cat : categories) {
                String key = stringVal(cat.get("key"));
                if (key.isBlank() || "communication".equals(key)) continue;
                int score = scoreCategoryMatch(text, cat);
                if (score > 0) {
                    scored.add(Map.entry(cat, score));
                }
            }
            scored.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
            for (int i = 0; i < Math.min(2, scored.size()); i++) {
                mappings.add(mappingFromCategory(scored.get(i).getKey()));
            }

            if (mappings.isEmpty()) {
                Map<String, Object> fallback = pickFallbackCategory(text, type, categories);
                if (fallback != null) {
                    mappings.add(mappingFromCategory(fallback));
                }
            }

            if (!"CODING".equals(type)) {
                mappings.add(mappingFromCategory(communication));
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("number", number);
            out.put("text", q.get("text"));
            out.put("difficulty", formatDifficulty(stringVal(q.get("difficulty"))));
            out.put("type", stringVal(q.get("type")));
            out.put("skillMappings", dedupeMappings(mappings));
            tagged.add(out);
        }
        return tagged;
    }

    private Map<String, Object> mappingFromCategory(Map<String, Object> cat) {
        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("categoryKey", cat.get("key"));
        mapping.put("skill", cat.get("label"));
        mapping.put("subSkill", cat.get("subSkill"));
        return mapping;
    }

    private List<Map<String, Object>> dedupeMappings(List<Map<String, Object>> mappings) {
        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> mapping : mappings) {
            String key = stringVal(mapping.get("categoryKey"));
            if (key.isBlank() || seen.contains(key)) continue;
            seen.add(key);
            out.add(mapping);
        }
        return out;
    }

    private int scoreCategoryMatch(String questionText, Map<String, Object> cat) {
        String haystack = questionText.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String token : categoryTokens(cat)) {
            if (token.length() < 3) continue;
            if (haystack.contains(token)) {
                score += token.length() >= 6 ? 3 : 2;
            }
        }
        return score;
    }

    private List<String> categoryTokens(Map<String, Object> cat) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String field : List.of("key", "label", "subSkill", "description")) {
            String value = stringVal(cat.get(field));
            if (value.isBlank()) continue;
            tokens.add(value.toLowerCase(Locale.ROOT).replace('_', ' '));
            for (String part : value.split("(?=[A-Z])|[^a-zA-Z0-9]+")) {
                String trimmed = part.trim().toLowerCase(Locale.ROOT);
                if (trimmed.length() >= 3) {
                    tokens.add(trimmed);
                }
            }
        }
        return new ArrayList<>(tokens);
    }

    private Map<String, Object> pickFallbackCategory(
            String questionText, String questionType, List<Map<String, Object>> categories) {
        String text = questionText.toLowerCase(Locale.ROOT);
        if (questionType.contains("BEHAVIORAL") || text.contains("career")
                || text.contains("role fit") || text.contains("why this role")
                || text.contains("want to learn")) {
            return categories.stream()
                .filter(cat -> {
                    String key = stringVal(cat.get("key"));
                    String label = stringVal(cat.get("label")).toLowerCase(Locale.ROOT);
                    return label.contains("communication") || label.contains("articulation")
                        || key.contains("communication") || key.contains("design");
                })
                .findFirst()
                .orElse(null);
        }
        return categories.stream()
            .filter(cat -> !"communication".equals(stringVal(cat.get("key"))))
            .findFirst()
            .orElse(null);
    }

    private List<Map<String, Object>> untaggedQuestions(List<Map<String, Object>> questions) {
        List<Map<String, Object>> tagged = new ArrayList<>();
        for (Map<String, Object> q : questions) {
            int number = parseInt(q.get("number"), tagged.size() + 1);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("number", number);
            out.put("text", q.get("text"));
            out.put("difficulty", formatDifficulty(stringVal(q.get("difficulty"))));
            out.put("type", stringVal(q.get("type")));
            out.put("skillMappings", List.of());
            tagged.add(out);
        }
        return tagged;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> generateBriefContent(
            String candidateName,
            String jdTitle,
            String jdText,
            String clientName,
            String seniorityBand,
            List<Map<String, Object>> categories,
            Map<String, Integer> scoresByKey,
            Map<String, Map<String, Object>> scoreDetails,
            Map<String, Object> assessment,
            String transcriptJson,
            List<Map<String, Object>> questions,
            String interviewId,
            String userId) throws Exception {

        StringBuilder scoresInfo = new StringBuilder();
        for (Map<String, Object> cat : categories) {
            String key = stringVal(cat.get("key"));
            Integer score = scoresByKey.get(key);
            if (score == null) continue;
            scoresInfo.append(key).append(" (").append(cat.get("label")).append(" / ")
                .append(cat.get("subSkill")).append("): ").append(score).append("/10\n");
            Map<String, Object> detail = scoreDetails.get(key);
            if (detail != null) {
                appendScoreDetailLines(scoresInfo, detail);
            }
        }

        StringBuilder categorySchema = new StringBuilder();
        for (Map<String, Object> cat : categories) {
            categorySchema.append("    {\n")
                .append("      \"categoryKey\": \"").append(cat.get("key")).append("\",\n")
                .append("      \"selectedProficiencyIndex\": 0-3,\n")
                .append("      \"strengths\": [\"3-5 bullets starting with ").append(candidateName).append("...\"],\n")
                .append("      \"areasOfImprovement\": [\"2-4 bullets starting with ").append(candidateName).append("...\"]\n")
                .append("    },\n");
        }

        String system =
            "You prepare a client-facing structured interview report in professional third-person language.\n" +
            "This document will be shared with an external hiring manager — keep the tone constructive, respectful, and diplomatic.\n" +
            "Candidate name: " + candidateName + "\n" +
            "Use the candidate name at the start of each bullet.\n" +
            "Return ONLY raw JSON. No markdown.\n" +
            "{\n" +
            "  \"overallFeedback\": \"4-6 sentence overview for the client\",\n" +
            "  \"skillAssessments\": [\n" + categorySchema +
            "  ]\n" +
            "}\n" +
            "RULES:\n" +
            "- overallFeedback MUST open with what the candidate demonstrated well, then note targeted growth areas.\n" +
            "- Frame gaps as coaching or preparation opportunities — never as failures or disqualifiers.\n" +
            "- Close overallFeedback with a constructive readiness outlook (e.g. suitable with mentoring, promising foundation).\n" +
            "- selectedProficiencyIndex picks ONE of the 4 proficiency options (0=strongest tier, 3=weakest).\n" +
            "- Map scores to proficiency: 8-10 -> 0, 6-7 -> 1, 3-5 -> 2, 1-2 -> 3.\n" +
            "- The numeric scores in the prompt are FINAL from the AI assessor — do not contradict or re-score them.\n" +
            "- Align overallFeedback and bullets with the supplied rationales, evidence, and gaps.\n" +
            "- Base bullets on scores, evidence, and transcript excerpts only — never use generic filler.\n" +
            "- For categoryKey=communication: evaluate HOW the candidate explained answers across the interview — " +
            "clarity, structure, depth, use of examples, trade-off reasoning, and completeness. " +
            "Reference specific answers or patterns from the transcript.\n" +
            "- Do not use harsh words like failed, liar, bad, incompetent, or rejected.\n" +
            "- Strength bullets should be genuine and specific; improvement bullets should be actionable and fair.\n" +
            "- Every category in the schema MUST appear in skillAssessments with non-empty bullets.";

        String jdSnippet = jdText != null && !jdText.isBlank()
            ? jdText.substring(0, Math.min(400, jdText.length())) : "";
        StringBuilder user = new StringBuilder();
        user.append("Role: ").append(jdTitle).append("\n");
        if (!clientName.isBlank()) {
            user.append("Client: ").append(clientName).append("\n");
        }
        if (!seniorityBand.isBlank()) {
            user.append("Candidate seniority: ").append(seniorityBand).append("\n");
        }
        user.append("JD: ").append(jdSnippet).append("\nSummary: ")
            .append(stringVal(assessment.get("summary"))).append("\n");
        String verdict = stringVal(assessment.get("proposedVerdict"));
        if (!verdict.isBlank()) {
            user.append("Internal readiness verdict: ").append(verdict)
                .append(" (translate into constructive client language — do not quote verbatim).\n");
        }
        user.append("Scores:\n").append(scoresInfo);
        appendBehavioralContext(user, assessment);
        user.append("\nQuestions asked:\n").append(formatQuestionsForPrompt(questions));
        user.append("\nInterview transcript (use for communication and evidence):\n")
            .append(buildTranscriptExcerpt(transcriptJson, 4000));

        String raw = llmClient.chatAssessmentWithTracking(system, user.toString(), interviewId, "client-brief");
        JsonNode json = objectMapper.readTree(JsonRepairUtil.repair(raw));

        Map<String, Object> out = new LinkedHashMap<>();
        String overall = json.path("overallFeedback").asText("");
        if (overall.isBlank()) {
            overall = stringVal(assessment.get("summary"));
        }
        out.put("overallFeedback", overall);

        List<Map<String, Object>> assessments = new ArrayList<>();
        json.path("skillAssessments").forEach(node -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("categoryKey", node.path("categoryKey").asText());
            item.put("selectedProficiencyIndex", node.path("selectedProficiencyIndex").asInt(1));
            item.put("strengths", toStringList(node.path("strengths")));
            item.put("areasOfImprovement", toStringList(node.path("areasOfImprovement")));
            assessments.add(item);
        });
        out.put("skillAssessments", assessments);
        return out;
    }

    private Map<String, Object> buildHeader(Map<String, Object> request) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("candidateName", request.get("candidateName"));
        header.put("candidateEmail", request.get("candidateEmail"));
        header.put("positionTitle", request.get("jdTitle"));
        header.put("clientName", request.get("clientName"));
        header.put("roundName", request.get("roundName"));
        header.put("seniorityBand", request.get("seniorityBand"));
        header.put("interviewDate", request.get("interviewDate"));
        header.put("totalMinutes", request.get("totalMinutes"));
        header.put("playbackUrl", request.get("playbackUrl"));
        header.put("resumeUrl", request.get("resumeUrl"));

        Map<String, Object> reviewer = new LinkedHashMap<>();
        reviewer.put("name", request.get("reviewerName"));
        reviewer.put("yearsExperience", request.get("reviewerYoe"));
        reviewer.put("company", request.get("reviewerCompany"));
        Object skills = request.get("reviewerSkills");
        reviewer.put("verifiedSkills", skills instanceof List<?> list ? list : List.of());
        header.put("reviewer", reviewer);
        return header;
    }

    private Map<String, Object> skillSummary(int rank, Map<String, Object> cat, int score10) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("rank", rank);
        summary.put("categoryKey", cat.get("key"));
        summary.put("skill", cat.get("label"));
        summary.put("subSkill", cat.get("subSkill"));
        summary.put("score10", score10);
        summary.put("note", cat.get("note"));
        return summary;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildSkillAssessment(
            int rank,
            Map<String, Object> cat,
            int score10,
            Map<String, Object> llmEntry,
            Map<String, Object> scoreDetail,
            String candidateName) {
        Map<String, Object> assessment = new LinkedHashMap<>();
        assessment.put("rank", rank);
        assessment.put("categoryKey", cat.get("key"));
        assessment.put("skill", cat.get("label"));
        assessment.put("subSkill", cat.get("subSkill"));

        Object optionsObj = cat.get("proficiencyOptions");
        List<String> options = optionsObj instanceof List<?> list
            ? list.stream().map(String::valueOf).toList()
            : defaultProficiencyOptions(stringVal(cat.get("subSkill")));
        assessment.put("proficiencyOptions", options);
        assessment.put("score10", score10);

        int selected = proficiencyIndexFromScore(score10);
        assessment.put("selectedProficiencyIndex", selected);

        String subSkill = stringVal(cat.get("subSkill"));
        List<String> strengths = bulletsFromScoreDetail(scoreDetail, candidateName, subSkill, true);
        List<String> improvements = bulletsFromScoreDetail(scoreDetail, candidateName, subSkill, false);

        if (strengths.isEmpty() && llmEntry.get("strengths") instanceof List<?> s) {
            strengths = s.stream().map(String::valueOf).filter(str -> !str.isBlank()).toList();
        }
        if (improvements.isEmpty() && llmEntry.get("areasOfImprovement") instanceof List<?> c) {
            improvements = c.stream().map(String::valueOf).filter(str -> !str.isBlank()).toList();
        }
        if (strengths.isEmpty()) {
            strengths = List.of(candidateName + " demonstrated working knowledge in " + cat.get("subSkill") + ".");
        }
        if (improvements.isEmpty()) {
            improvements = List.of(candidateName + " would benefit from additional hands-on practice in "
                + cat.get("subSkill") + ".");
        }
        assessment.put("strengths", strengths);
        assessment.put("areasOfImprovement", improvements);
        return assessment;
    }

    private void appendScoreDetailLines(StringBuilder sb, Map<String, Object> detail) {
        if (!stringVal(detail.get("rationale")).isBlank()) {
            sb.append("  rationale: ").append(detail.get("rationale")).append("\n");
        }
        if (!stringVal(detail.get("evidence")).isBlank()) {
            sb.append("  evidence: ").append(detail.get("evidence")).append("\n");
        }
        if (!stringVal(detail.get("gap")).isBlank()) {
            sb.append("  gap: ").append(detail.get("gap")).append("\n");
        }
        List<String> strengths = parseStringList(detail.get("strengths"));
        if (!strengths.isEmpty()) {
            sb.append("  strengths: ").append(String.join("; ", strengths)).append("\n");
        }
        List<String> weaknesses = parseStringList(detail.get("weaknesses"));
        if (!weaknesses.isEmpty()) {
            sb.append("  weaknesses: ").append(String.join("; ", weaknesses)).append("\n");
        }
    }

    @SuppressWarnings("unchecked")
    private void appendBehavioralContext(StringBuilder sb, Map<String, Object> assessment) {
        Object behavioralObj = assessment.get("behavioralSignals");
        if (!(behavioralObj instanceof Map<?, ?> behavioral)) return;
        sb.append("\nBehavioral signals:\n");
        sb.append("  communicationStructure: ").append(behavioral.get("communicationStructure")).append("\n");
        sb.append("  confidenceCalibration: ").append(behavioral.get("confidenceCalibration")).append("\n");
        sb.append("  summary: ").append(behavioral.get("summary")).append("\n");
    }

    private String formatQuestionsForPrompt(List<Map<String, Object>> questions) {
        if (questions.isEmpty()) return "(none)\n";
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> q : questions) {
            sb.append("Q").append(q.get("number")).append(" [").append(q.get("type")).append("]: ")
                .append(truncate(stringVal(q.get("text")), 400)).append("\n");
        }
        return sb.toString();
    }

    private String buildTranscriptExcerpt(String transcriptJson, int maxChars) {
        List<Map<String, String>> utterances = parseUtterances(transcriptJson);
        if (utterances.isEmpty()) return "(no transcript available)\n";
        StringBuilder sb = new StringBuilder();
        for (Map<String, String> utterance : utterances) {
            sb.append(utterance.get("speaker")).append(": ")
                .append(truncate(utterance.get("text"), 700)).append("\n");
            if (sb.length() >= maxChars) break;
        }
        return sb.length() > maxChars ? sb.substring(0, maxChars) + "\n...(truncated)" : sb.toString();
    }

    private List<Map<String, String>> parseUtterances(String transcriptJson) {
        if (transcriptJson == null || transcriptJson.isBlank()) return List.of();
        try {
            JsonNode doc = objectMapper.readTree(transcriptJson);
            JsonNode arr = doc.path("utterances");
            List<Map<String, String>> list = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode u : arr) {
                    list.add(Map.of(
                        "speaker", u.path("speaker").asText("CANDIDATE"),
                        "text", u.path("text").asText("")));
                }
            }
            return list;
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> bulletsFromScoreDetail(
            Map<String, Object> detail, String candidateName, String subSkill, boolean strength) {
        if (detail == null) return List.of();
        String field = strength ? "strengths" : "weaknesses";
        List<String> fromField = parseStringList(detail.get(field));
        if (!fromField.isEmpty()) {
            return prefixBullets(fromField, candidateName);
        }
        if (strength) {
            String evidence = stringVal(detail.get("evidence"));
            if (!evidence.isBlank()) {
                return List.of(prefixCandidate(candidateName, evidence));
            }
            String rationale = stringVal(detail.get("rationale"));
            if (!rationale.isBlank()) {
                return List.of(prefixCandidate(candidateName, "showed relevant understanding: " + truncate(rationale, 220)));
            }
        } else {
            String gap = stringVal(detail.get("gap"));
            if (!gap.isBlank()) {
                return List.of(prefixCandidate(candidateName, gap));
            }
            String rationale = stringVal(detail.get("rationale"));
            if (!rationale.isBlank()) {
                return List.of(rationale.startsWith(candidateName) ? rationale
                    : prefixCandidate(candidateName, "needs to improve " + subSkill.toLowerCase() + ": " + rationale));
            }
        }
        return List.of();
    }

    private List<String> prefixBullets(List<String> items, String candidateName) {
        List<String> out = new ArrayList<>();
        for (String item : items) {
            if (item.isBlank()) continue;
            out.add(item.startsWith(candidateName) ? item : prefixCandidate(candidateName, item));
        }
        return out;
    }

    private String prefixCandidate(String candidateName, String text) {
        if (text.startsWith(candidateName)) return text;
        return candidateName + " " + text;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseStringList(Object value) {
        if (value == null) return List.of();
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item != null && !item.toString().isBlank()) out.add(item.toString());
            }
            return out;
        }
        String text = value.toString().trim();
        if (text.isBlank() || "[]".equals(text)) return List.of();
        if (text.startsWith("[") && text.endsWith("]")) {
            try {
                JsonNode node = objectMapper.readTree(text);
                if (node.isArray()) return toStringList(node);
            } catch (Exception ignored) {}
        }
        return List.of(text);
    }

    private Map<String, Map<String, Object>> mapByCategoryKey(List<Map<String, Object>> items) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            out.put(stringVal(item.get("categoryKey")), item);
        }
        return out;
    }

    private int proficiencyIndexFromScore(int score10) {
        if (score10 >= 8) return 0;
        if (score10 >= 6) return 1;
        if (score10 >= 3) return 2;
        return 3;
    }

    private List<String> defaultProficiencyOptions(String subSkill) {
        return List.of(
            "Strong knowledge of " + subSkill,
            "Good knowledge of " + subSkill,
            "Only theoretical knowledge of " + subSkill + " with no practical experience",
            "No knowledge of " + subSkill
        );
    }

    private List<Map<String, Object>> defaultCategories() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(category("coreJava", "Core Java", "Java Fundamentals", 3, "MUST_HAVE"));
        list.add(category("spring", "Spring Boot", "Spring Boot Fundamentals", 3, "MUST_HAVE"));
        list.add(category("microservices", "Software Architecture", "Microservices Architecture", 2, "GOOD_TO_HAVE"));
        list.add(category("miscellaneous", "Problem Solving", "Case Study Problem-Solving", 2, "GOOD_TO_HAVE"));
        return list;
    }

    private Map<String, Object> category(String key, String label, String subSkill, int weight, String priority) {
        Map<String, Object> cat = new LinkedHashMap<>();
        cat.put("key", key);
        cat.put("label", label);
        cat.put("subSkill", subSkill);
        cat.put("description", subSkill);
        cat.put("weight", weight);
        cat.put("priority", priority);
        cat.put("note", "");
        cat.put("proficiencyOptions", defaultProficiencyOptions(subSkill));
        return cat;
    }

    private List<String> toStringList(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node.isArray()) node.forEach(n -> out.add(n.asText()));
        return out;
    }

    private String formatDifficulty(String difficulty) {
        if (difficulty == null || difficulty.isBlank()) return "Medium";
        return difficulty.substring(0, 1).toUpperCase() + difficulty.substring(1).toLowerCase();
    }

    private String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private String stringVal(Object value) {
        return value != null ? value.toString() : "";
    }

    private int parseInt(Object value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }
}
