package com.benchreadiness.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Parses a pasted, free-form screening checklist document (like a client's role-specific
 * "Proceed / Reject / Validate" checklist with a weighted rating matrix) into the structured
 * JSON schema the rest of the platform uses to drive rubric generation, question focus, and
 * checklist-aware scoring. This is a one-time parse per client/role — the result is saved and
 * reused for every interview against that client, not re-parsed per candidate.
 */
@Service
public class ScreeningChecklistService {

    private static final Logger log = LoggerFactory.getLogger(ScreeningChecklistService.class);

    private static final String SYSTEM_PROMPT = """
            You are a precise document parser. Convert a raw, free-form technical screening checklist
            (proceed/reject rules, a weighted rating matrix, knockout questions, recommendation bands)
            into the exact JSON schema below. Extract only what is explicitly present — do not invent
            dimensions, weights, or questions that are not in the source text.

            OUTPUT SCHEMA (respond with ONLY this JSON, no markdown, no prose):
            {
              "ratingScale": {
                "min": 1,
                "max": 5,
                "levels": [
                  {"score": 1, "definition": "What a score of 1 means, from the source"},
                  {"score": 5, "definition": "What the top score means, from the source"}
                ]
              },
              "dimensions": [
                {"key": "camelCaseKey", "label": "Human label exactly as in the source", "weightPct": 20, "goodLooksLike": "What good looks like, from the source"}
              ],
              "proceedGates": ["Each 'proceed only if' condition as its own string"],
              "rejectSignals": ["Each 'immediate reject' condition as its own string"],
              "validateFlags": ["Each 'needs careful validation' condition as its own string"],
              "knockoutQuestions": ["Each literal knockout/must-ask question as its own string"],
              "recommendationBands": [
                {"min": 4.0, "max": 5.0, "label": "Strong proceed", "action": "Action text from the source"}
              ]
            }

            RULES:
            1. dimensions: one entry per row of the rating matrix. weightPct values MUST be the percentages from
               the source (e.g. "20%" -> 20) and MUST sum to 100 — if the source doesn't sum to 100, keep the
               source values as-is (do not silently rebalance them) and note nothing extra, just extract faithfully.
            2. key: short camelCase derived from the label (e.g. "Native iOS development depth" -> "nativeIosDevelopmentDepth").
            3. recommendationBands: extract the exact numeric ranges and action text from the source's
               recommendation table. The band numbers MUST be on the same scale as ratingScale (do not
               normalize or convert them).
            4. ratingScale: extract the source's own scoring scale (min, max, and the definition text for each
               numbered level, e.g. "1 = No evidence...", "5 = Expert-level..."). Include every level the source
               explicitly defines, in ascending score order. If the source does not define a rating scale at all
               (no per-level definitions and no min/max stated anywhere), default to min=1, max=5 with these
               generic levels: 1="No evidence or a fundamental gap", 2="Basic/theoretical knowledge only",
               3="Adequate hands-on experience; meets the baseline requirement", 4="Strong hands-on experience with
               clear examples", 5="Expert-level; exceeds the requirement". Never invent a scale that contradicts
               numbers actually present in the source (e.g. if the source's matrix or bands use a 1-10 scale,
               ratingScale.max must be 10, not 5).
            5. If a section (e.g. validateFlags) is absent from the source, return an empty array for it — do not fabricate.
            6. Preserve the source's own wording for gate/signal/question text as closely as possible; light
               grammar cleanup only.
            """;

    private static final String USER_PROMPT_TEMPLATE = """
            Parse the following screening checklist document into the schema above.

            === START OF SOURCE DOCUMENT ===
            %s
            === END OF SOURCE DOCUMENT ===
            """;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public ScreeningChecklistService(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    public JsonNode parse(String rawChecklistText) {
        if (!llmClient.isConfigured()) {
            throw new IllegalStateException("LLM provider is not configured. Set APP_CLAUDE_API_KEY or configure Ollama.");
        }
        if (rawChecklistText == null || rawChecklistText.isBlank()) {
            throw new IllegalArgumentException("Checklist text is required");
        }

        String userPrompt = String.format(USER_PROMPT_TEMPLATE, rawChecklistText);
        log.info("[ScreeningChecklist] Parsing checklist document ({} chars)", rawChecklistText.length());

        try {
            String raw = llmClient.chatDigest(SYSTEM_PROMPT, userPrompt);
            raw = JsonRepairUtil.repair(raw);
            JsonNode result = objectMapper.readTree(raw);
            log.info("[ScreeningChecklist] Parsed {} dimensions, {} reject signals, {} knockout questions",
                    result.path("dimensions").size(), result.path("rejectSignals").size(),
                    result.path("knockoutQuestions").size());
            return result;
        } catch (Exception e) {
            log.error("[ScreeningChecklist] LLM parse failed: {}", e.getMessage(), e);
            throw new RuntimeException("Screening checklist parse failed: " + e.getMessage(), e);
        }
    }
}
