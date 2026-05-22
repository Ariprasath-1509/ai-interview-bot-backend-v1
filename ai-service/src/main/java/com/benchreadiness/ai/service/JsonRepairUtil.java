package com.benchreadiness.ai.service;

/**
 * Lightweight JSON repair applied before Jackson parsing.
 * Handles the most common local-model output defects:
 *   - <think>...</think> reasoning leakage (deepseek-r1, qwen3 thinking mode)
 *   - Trailing commas before } or ]
 *   - Unquoted enum values for known string fields
 *   - Truncated JSON — missing closing braces / brackets
 */
public final class JsonRepairUtil {

    private JsonRepairUtil() {}

    public static String repair(String raw) {
        if (raw == null || raw.isBlank()) return "{}";

        String s = raw.trim();

        // 1. Strip <think>...</think> blocks (deepseek-r1 / qwen3 thinking leakage)
        s = s.replaceAll("(?s)<think>.*?</think>", "").trim();

        // 2. Strip markdown fences that survived the Ollama client stripping
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```[a-zA-Z]*\\s*", "");
            int end = s.lastIndexOf("```");
            if (end != -1) s = s.substring(0, end);
            s = s.trim();
        }

        // 3. Extract the outermost JSON object if prose surrounds it
        int jsonStart = s.indexOf('{');
        int jsonEnd   = s.lastIndexOf('}');
        if (jsonStart == -1) return "{}";
        if (jsonEnd > jsonStart) s = s.substring(jsonStart, jsonEnd + 1);
        else s = s.substring(jsonStart); // truncated — will be fixed below

        // 4. Remove trailing commas before } or ]
        s = s.replaceAll(",\\s*([}\\]])", "$1");

        // 5. Quote known unquoted enum values that small models sometimes emit bare
        //    e.g.  "source": QUESTION_BANK  →  "source": "QUESTION_BANK"
        s = s.replaceAll("(\"(?:source|recommendation|proposedVerdict|level|confidence|ownershipLevel|learningAgility|communicationStructure|confidenceCalibration|questionDifficulty)\")\\s*:\\s*([A-Z_a-z][A-Z_a-z0-9]*)",
                         "$1: \"$2\"");

        // 6. Balance unclosed braces / brackets (truncation recovery)
        s = balanceBrackets(s);

        return s;
    }

    private static String balanceBrackets(String s) {
        int braces   = 0;
        int brackets = 0;
        boolean inString = false;
        boolean escape   = false;

        for (char c : s.toCharArray()) {
            if (escape)          { escape = false; continue; }
            if (c == '\\')       { escape = true;  continue; }
            if (c == '"')        { inString = !inString; continue; }
            if (inString)        continue;
            if (c == '{')        braces++;
            else if (c == '}')   braces--;
            else if (c == '[')   brackets++;
            else if (c == ']')   brackets--;
        }

        // Trim any trailing comma that appeared just before we ran out of content
        s = s.replaceAll(",\\s*$", "");

        StringBuilder sb = new StringBuilder(s);
        while (brackets > 0) { sb.append(']'); brackets--; }
        while (braces   > 0) { sb.append('}'); braces--;   }
        return sb.toString();
    }
}
