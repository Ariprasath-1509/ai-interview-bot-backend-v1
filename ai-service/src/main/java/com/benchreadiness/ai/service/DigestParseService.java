package com.benchreadiness.ai.service;

import com.benchreadiness.ai.dto.DigestAiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Handles AI-driven parsing of raw interview text into structured sessions and questions.
 * Called by the questionbank-service via POST /ai/digest-parse so that all LLM calls
 * are managed in one place (ai-service).
 */
@Service
public class DigestParseService {

    private static final Logger log = LoggerFactory.getLogger(DigestParseService.class);

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are a deterministic, expert interview question parser engine. Your sole function is to extract highly structured, clean data from raw, unstructured interview transcript text blocks.

            ALLOWED SYSTEM CATEGORIES (You MUST pick exactly one per extracted question):
            {categoryList}

            STRICT PARSING & PROCESSING RULES:
            1. DATA INTEGRITY: Extract every distinct question asked during the session. Split compound or multi-part questions into individual, single-sentence records.
            2. CLEANING: Keep question text clean and concise. Fix shorthand typing grammar or missing punctuation, but strictly preserve the original technical meaning.
            3. METADATA NORMALIZATION:
               - Missing Values: If data is completely absent, use "Unknown" for names and null for dates.
               - Round Field: Force exactly one uppercase value from this allowed list: L1, L2, SCREENING, HR, F2F, TECHNICAL, MANAGERIAL.
               - Category Mapping: Match strictly to the allowed list above. If a question intersects multiple categories or you are unsure, fall back to "General". Do NOT invent new categories.
               - Tags Format: Provide 2-4 granular tags per question. Must be entirely lowercase, using hyphens instead of spaces (e.g., "event-driven", "spring-boot", "design-patterns").
               - Date Format: Always force standard ISO format "YYYY-MM-DD". If the text contains relative or spoken dates (e.g., "29th April 2026"), convert it mathematically to "2026-04-29".
            4. DUPLICATE COLLAPSE: If the same technical question appears multiple times with only wording variations, keep only the single clearest and most complete normalized version.
            5. QUESTION VALIDATION: Discard any entry that is not a genuine, actionable interview question. Do NOT extract conversational filler, transition statements, or commentary.
            6. QUESTION LENGTH: Question text must not exceed 220 characters. If a raw question exceeds this limit, extract only the core technical question.

            STRICT OUTPUT PROTOCOL:
            - Respond with EXACTLY and ONLY a valid JSON object with this structure:
              {"sessions":[{"candidateName":"...","company":"...","round":"...","date":"YYYY-MM-DD or null","interviewer":"...","questions":[{"text":"...","category":"...","suggestedTags":["tag1","tag2"],"confidence":0.95}]}]}
            - Do NOT include markdown fences, backticks, or any text outside the JSON object.
            """;

    private static final String USER_PROMPT_TEMPLATE = """
            Parse the following raw interview text and return the structured JSON as instructed.

            === START OF SOURCE TEXT ===
            {rawText}
            === END OF SOURCE TEXT ===
            """;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public DigestParseService(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Parse raw interview text into structured AI response.
     *
     * @param rawText      the pasted interview text
     * @param categoryList comma-separated list of allowed category names from the QB DB
     * @return structured sessions with extracted questions
     */
    public DigestAiResponse parse(String rawText, String categoryList) {
        if (!llmClient.isConfigured()) {
            throw new IllegalStateException("LLM provider is not configured. Set APP_CLAUDE_API_KEY or configure Ollama.");
        }

        String systemPrompt = SYSTEM_PROMPT_TEMPLATE.replace("{categoryList}",
                categoryList != null && !categoryList.isBlank() ? categoryList : "General, Java, Python, DSA, System Design");
        String userPrompt = USER_PROMPT_TEMPLATE.replace("{rawText}", rawText);

        log.info("[DigestParse] Calling LLM to parse interview text ({} chars, categories: {})",
                rawText.length(), categoryList);

        try {
            String raw = llmClient.chatDigest(systemPrompt, userPrompt);
            raw = JsonRepairUtil.repair(raw);

            DigestAiResponse result = objectMapper.readValue(raw, DigestAiResponse.class);
            log.info("[DigestParse] Parsed {} sessions successfully", result.sessions().size());
            return result;
        } catch (Exception e) {
            log.error("[DigestParse] LLM parse failed: {}", e.getMessage(), e);
            throw new RuntimeException("Digest parse failed: " + e.getMessage(), e);
        }
    }
}
