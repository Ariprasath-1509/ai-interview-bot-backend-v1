package com.benchreadiness.ai.service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Centralised prompt-injection and score-manipulation detector.
 *
 * Used by:
 *   - QuestionService  — per-answer check during live interview
 *   - AssessmentService — full-transcript pre-filter before LLM call
 */
public final class InjectionGuard {

    private InjectionGuard() {}

    public static final int TERMINATE_THRESHOLD = 5;

    private static final List<Pattern> PATTERNS = List.of(
        Pattern.compile("(?i)(give me|award me|score me).{0,30}(full|high|good|max|perfect|5|10)\\s*(mark|score|point|rating)"),
        Pattern.compile("(?i)(ignore|forget|disregard).{0,30}(previous|prior|above|instruction|prompt|rule)"),
        Pattern.compile("(?i)(only ask|stick to|ask only|focus only on|just ask).{0,40}(topic|question|subject|area)"),
        Pattern.compile("(?i)(i (know|am good at|am expert in|am strong in)).{0,30}(only|just).{0,30}(ask|question)"),
        Pattern.compile("(?i)(assess|mark|rate|evaluate) me as (ready|excellent|good|strong|perfect)"),
        Pattern.compile("(?i)(you are now|act as|pretend to be|you are a different|new instruction)"),
        Pattern.compile("(?i)(i answered (everything|all|correctly|perfectly|well))"),
        Pattern.compile("(?i)(don.t ask|do not ask|avoid asking|skip).{0,30}(question|topic|area)")
    );

    /** Check a single answer string. Returns true if any pattern matches. */
    public static boolean isInjection(String text) {
        if (text == null || text.isBlank()) return false;
        return PATTERNS.stream().anyMatch(p -> p.matcher(text).find());
    }

    /**
     * Scan the full transcript utterance list.
     * Returns the total number of candidate utterances that contain injection patterns.
     * Caller decides what to do based on the count vs TERMINATE_THRESHOLD.
     */
    public static int countTranscriptInjections(List<Map<String, String>> utterances) {
        if (utterances == null || utterances.isEmpty()) return 0;
        return (int) utterances.stream()
            .filter(u -> "CANDIDATE".equals(u.get("speaker")))
            .map(u -> u.getOrDefault("text", ""))
            .filter(InjectionGuard::isInjection)
            .count();
    }
}
