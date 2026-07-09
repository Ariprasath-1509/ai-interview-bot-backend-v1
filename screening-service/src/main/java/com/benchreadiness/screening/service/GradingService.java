package com.benchreadiness.screening.service;

import com.benchreadiness.screening.entity.ScreeningQuestion;
import com.benchreadiness.screening.entity.enums.QuestionType;
import com.benchreadiness.screening.llm.ScreeningLlmClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class GradingService {

    public static class GradeResult {
        public final double score;
        public final String feedback;
        public GradeResult(double score, String feedback) { this.score = score; this.feedback = feedback; }
    }

    private final ScreeningLlmClient llmClient;
    private final ObjectMapper objectMapper;

    public GradingService(ScreeningLlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    /** MCQ is graded instantly, no LLM call — exact match against the stored correct option text. */
    public GradeResult gradeMcq(ScreeningQuestion question, String rawAnswer) {
        boolean correct = rawAnswer != null
                && question.getReferenceAnswer() != null
                && rawAnswer.trim().equalsIgnoreCase(question.getReferenceAnswer().trim());
        return new GradeResult(correct ? question.getMarks() : 0,
                correct ? "Correct." : "Incorrect — expected: " + question.getReferenceAnswer());
    }

    public GradeResult gradeSnippet(ScreeningQuestion question, String rawAnswer) throws Exception {
        return gradeViaLlm(question, rawAnswer,
                "You are grading a short-answer code-snippet question worth " + question.getMarks() + " mark(s), " +
                "scored as either 0 or " + question.getMarks() + " (no partial credit). Judge whether the " +
                "candidate's answer is substantively correct, even if worded differently from the reference.");
    }

    /** The 2-of-4 free-text pseudocode/code questions — no editor, no execution, judged purely by LLM reasoning. */
    public GradeResult gradeLogical(ScreeningQuestion question, String rawAnswer) throws Exception {
        return gradeViaLlm(question, rawAnswer,
                "You are grading a pseudocode/code answer worth up to " + question.getMarks() + " marks (integer " +
                "or half-point scores allowed, from 0 to " + question.getMarks() + "). There is no code editor or " +
                "execution — judge correctness of logic/approach as written. Give a specific, concrete reason " +
                "for the score, especially exactly why it fails if it is wrong or incomplete.");
    }

    public GradeResult gradeProficiency(ScreeningQuestion question, String rawAnswer) throws Exception {
        String system = "You are assessing written English proficiency from a short answer to the question '" +
                question.getPrompt() + "', scored 0 to " + question.getMarks() + ". Judge grammar, clarity, and " +
                "coherence of the English — not the persuasiveness of the content. Respond ONLY with strict JSON: " +
                "{\"score\": number, \"feedback\": string}.";
        String user = "Candidate answer:\n" + safe(rawAnswer);
        String raw = llmClient.gradeAnswer(system, user);
        return parseGradeJson(raw, question.getMarks());
    }

    private GradeResult gradeViaLlm(ScreeningQuestion question, String rawAnswer, String scoringInstruction) throws Exception {
        String system = scoringInstruction + " Respond ONLY with strict JSON: {\"score\": number, \"feedback\": string}. " +
                "The feedback must explain concretely why the answer is correct, partially correct, or wrong.";
        String user = "Question:\n" + question.getPrompt() +
                "\n\nReference (for grading only, not shown to candidate):\n" + safe(question.getReferenceAnswer()) +
                "\n\nCandidate's answer:\n" + safe(rawAnswer);
        String raw = llmClient.gradeAnswer(system, user);
        return parseGradeJson(raw, question.getMarks());
    }

    private GradeResult parseGradeJson(String raw, int maxMarks) throws Exception {
        JsonNode node;
        try {
            node = objectMapper.readTree(raw);
        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            // LLM returned unescaped quotes inside string values — extract score/feedback via regex
            java.util.regex.Matcher scoreMatcher = java.util.regex.Pattern
                    .compile("\"score\"\\s*:\\s*([0-9.]+)").matcher(raw);
            java.util.regex.Matcher feedbackMatcher = java.util.regex.Pattern
                    .compile("\"feedback\"\\s*:\\s*\"(.*)\"", java.util.regex.Pattern.DOTALL).matcher(raw);
            double score = scoreMatcher.find() ? Double.parseDouble(scoreMatcher.group(1)) : 0;
            String feedback = feedbackMatcher.find() ? feedbackMatcher.group(1).replaceAll("\"\\s*\\}\\s*$", "").trim() : raw;
            return new GradeResult(Math.max(0, Math.min(maxMarks, score)), feedback);
        }
        double score = node.path("score").asDouble(0);
        score = Math.max(0, Math.min(maxMarks, score));
        String feedback = node.path("feedback").asText("");
        return new GradeResult(score, feedback);
    }

    private String safe(String s) {
        return (s == null || s.isBlank()) ? "(no answer provided)" : s;
    }
}
