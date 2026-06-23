package com.qb.ai.llm;

/**
 * Lightweight JSON extraction and repair utility for local LLM output.
 * Handles:
 *   - <think>...</think> reasoning leakage (deepseek-r1, qwen3 thinking mode)
 *   - Markdown fence stripping
 *   - Outermost JSON object extraction
 *   - Trailing comma removal before } or ]
 *   - Bracket balancing for truncated responses
 */
public final class JsonRepairUtil {

    private JsonRepairUtil() {}

    public static String extractJson(String rawLlmOutput) {
        if (rawLlmOutput == null || rawLlmOutput.isBlank()) return "{}";

        // 1. Strip <think>...</think> blocks
        String clean = rawLlmOutput.replaceAll("(?s)<think>.*?</think>", "").trim();

        // 2. Strip markdown fences
        if (clean.startsWith("```")) {
            clean = clean.replaceFirst("^```[a-zA-Z]*\\s*", "");
            int end = clean.lastIndexOf("```");
            if (end != -1) clean = clean.substring(0, end);
            clean = clean.trim();
        }

        // 3. Extract outermost JSON object
        int firstBrace = clean.indexOf('{');
        int lastBrace  = clean.lastIndexOf('}');
        if (firstBrace != -1 && lastBrace > firstBrace) {
            clean = clean.substring(firstBrace, lastBrace + 1);
        } else if (firstBrace != -1) {
            // Truncated — take from first brace to end, repair below
            clean = clean.substring(firstBrace);
        }

        // 4. Remove trailing commas before } or ]
        clean = clean.replaceAll(",\\s*([}\\]])", "$1");

        // 5. Balance unclosed braces and brackets
        clean = balanceBrackets(clean);

        return clean;
    }

    private static String balanceBrackets(String s) {
        int braces   = 0;
        int brackets = 0;
        boolean inString = false;
        boolean escape   = false;

        for (char c : s.toCharArray()) {
            if (escape)        { escape = false; continue; }
            if (c == '\\')     { escape = true;  continue; }
            if (c == '"')      { inString = !inString; continue; }
            if (inString)      continue;
            if (c == '{')      braces++;
            else if (c == '}') braces--;
            else if (c == '[') brackets++;
            else if (c == ']') brackets--;
        }

        // Trim trailing comma that appeared just before truncation
        s = s.replaceAll(",\\s*$", "");

        StringBuilder sb = new StringBuilder(s);
        while (brackets > 0) { sb.append(']'); brackets--; }
        while (braces   > 0) { sb.append('}'); braces--;   }
        return sb.toString();
    }
}
