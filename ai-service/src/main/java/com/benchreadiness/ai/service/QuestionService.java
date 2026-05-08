package com.benchreadiness.ai.service;

import com.benchreadiness.ai.dto.NextQuestionRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class QuestionService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(QuestionService.class);

    public record QuestionResult(String question, boolean manipulationDetected, boolean terminateInterview) {}

    private static final int MANIPULATION_WARN_THRESHOLD = 1;
    private static final int MANIPULATION_TERMINATE_THRESHOLD = 5;

    // Patterns that indicate prompt injection or score manipulation
    private static final List<Pattern> MANIPULATION_PATTERNS = List.of(
        Pattern.compile("(?i)(give me|give me a|award me|score me).{0,30}(full|high|good|max|perfect|5|10)\\s*(mark|score|point|rating)"),
        Pattern.compile("(?i)(ignore|forget|disregard).{0,30}(previous|prior|above|instruction|prompt|rule)"),
        Pattern.compile("(?i)(only ask|stick to|ask only|focus only on|just ask).{0,40}(topic|question|subject|area)"),
        Pattern.compile("(?i)(i (know|am good at|am expert in|am strong in)).{0,30}(only|just).{0,30}(ask|question)"),
        Pattern.compile("(?i)(assess|mark|rate|evaluate) me as (ready|excellent|good|strong|perfect)"),
        Pattern.compile("(?i)(you are now|act as|pretend to be|you are a different|new instruction)"),
        Pattern.compile("(?i)(i answered (everything|all|correctly|perfectly|well))"),
        Pattern.compile("(?i)(don.t ask|do not ask|avoid asking|skip).{0,30}(question|topic|area)")
    );

    private static final Map<String, Map<Integer, String>> MODE_SLOT_THEMES = Map.of(
        "SCREENING", Map.of(
            1, "Basic intro — establish context: what they've built, their primary stack, and the scale they've operated under.",
            2, "Core concept check — pick one fundamental topic from the JD: basic understanding and practical use.",
            3, "Simple problem solving — basic algorithm or logic problem relevant to the role.",
            4, "Communication — explain something technical simply to a non-technical stakeholder.",
            5, "Role fit — why this role, what they want to learn, and their career direction."
        ),
        "L1", Map.of(
            1, "Technical opener — recent project walkthrough: problem, solution, and your specific contributions.",
            2, "Core skills — fundamentals depth in primary technology stack from JD.",
            3, "Problem solving — basic algorithm or logic problem with explanation of approach.",
            4, "Implementation details — how they build things: testing, code quality, debugging approach.",
            5, "Testing & quality — their approach to correctness, testing strategies, quality assurance.",
            6, "Learning & growth — how they stay current with technology, learning from failures.",
            7, "Team collaboration — working with others, code reviews, knowledge sharing."
        ),
        "L2", Map.of(
            1, "System overview — architecture they've worked on: components, data flow, scale.",
            2, "Trade-offs & decisions — competing concerns: speed vs correctness, consistency vs availability.",
            3, "Real-world scenarios — production challenges: performance issues, scaling problems, incidents.",
            4, "Data & consistency — how they handle state: databases, caching, transactions.",
            5, "Performance & scale — optimization experience: bottlenecks, profiling, scaling strategies.",
            6, "Debugging & troubleshooting — incident response: investigation, root cause analysis, prevention.",
            7, "Design patterns — when and why to use them: practical application, trade-offs.",
            8, "Integration challenges — working with external systems: APIs, third-party services, reliability."
        ),
        "L3", Map.of(
            1, "Architecture design — system they've architected: requirements, constraints, design decisions.",
            2, "Distributed systems — consistency, availability, partition tolerance: CAP theorem in practice.",
            3, "Failure handling — cascading failures, circuit breakers, resilience patterns.",
            4, "Performance at scale — bottlenecks and optimization: profiling, caching, database optimization.",
            5, "Data architecture — storage, caching, replication: consistency models, eventual consistency.",
            6, "Monitoring & observability — how they instrument systems: metrics, logging, alerting.",
            7, "Security considerations — threat modeling, defense in depth, security by design.",
            8, "Technical leadership — influencing technical decisions: architecture reviews, technical debt.",
            9, "System evolution — refactoring large systems: migration strategies, backward compatibility.",
            10, "Complex problem solving — ambiguous technical challenges: breaking down problems, risk assessment."
        ),
        "L4", Map.of(
            1, "System design at scale — design a distributed system: requirements gathering, architecture.",
            2, "Architecture trade-offs — CAP theorem, consistency models: when to choose what and why.",
            3, "Failure handling — chaos engineering, resilience patterns: designing for failure.",
            4, "Cross-team impact — how they influenced architecture decisions across multiple teams.",
            5, "Ambiguity handling — vague requirement to concrete plan: stakeholder management, technical strategy.",
            6, "Technical strategy — long-term technical vision: technology roadmap, architectural evolution.",
            7, "Organizational scaling — technical decisions across teams: standards, platforms, tooling.",
            8, "Innovation & research — exploring new technologies: evaluation criteria, adoption strategy.",
            9, "Mentorship & growth — developing other engineers: technical leadership, knowledge transfer.",
            10, "Business impact — connecting technical decisions to outcomes: metrics, ROI, business alignment."
        )
    );

    private final LlmClient llmClient;
    private final QuestionCacheService cacheService;

    public QuestionService(LlmClient llmClient, QuestionCacheService cacheService) {
        this.llmClient = llmClient;
        this.cacheService = cacheService;
    }

    public QuestionResult getNextQuestion(NextQuestionRequest req, String userId) {
        // Check for manipulation
        ManipulationCheck check = checkManipulation(req.getLastAnswer(), req.getManipulationCount());

        if (check.terminate()) {
            return new QuestionResult(
                "This interview is being terminated. Multiple attempts to manipulate the AI interviewer have been detected and flagged for review.",
                true, true
            );
        }

        if (check.warn()) {
            return new QuestionResult(
                "Please answer the interview questions directly. Attempts to influence the AI or manipulate scores are not allowed and have been noted.",
                true, false
            );
        }

        // Normal flow
        if (llmClient.isConfigured()) {
            try {
                // Check for vague/short answers first
                // IMPORTANT: don't get stuck in probe loops. If we already asked a probe/fallback,
                // do not probe again; continue to the next real question.
                if (isVagueAnswer(req.getLastAnswer()) && !lastQuestionWasProbe(req.getUtterances())) {
                    return new QuestionResult(getVagueAnswerProbe(), false, false);
                }
                
                // Check cache for first question
                if (req.getSlot() == 1 && (req.getLastAnswer() == null || req.getLastAnswer().isBlank())) {
                    String cached = cacheService.getCachedFirstQuestion(
                        req.getJdTitle(), req.getJdText(), req.getFocusAreas(), req.getInterviewMode());
                    if (cached != null) {
                        return new QuestionResult(cached, false, false);
                    }
                }
                
                String question = llmQuestion(req, userId);
                
                // Cache first question if this was slot 1
                if (req.getSlot() == 1 && (req.getLastAnswer() == null || req.getLastAnswer().isBlank())) {
                    cacheService.cacheFirstQuestion(
                        req.getJdTitle(), req.getJdText(), req.getFocusAreas(), req.getInterviewMode(), question);
                }
                
                return new QuestionResult(question, false, false);
            } catch (Exception e) {
                log.warn("Claude failed, falling back to heuristic: {}", e.getMessage());
            }
        } else {
            log.warn("LLM provider not configured — falling back to heuristic");
        }
        return new QuestionResult(fallbackQuestion(req), false, false);
    }

    private boolean lastQuestionWasProbe(List<NextQuestionRequest.Utterance> utterances) {
        if (utterances == null || utterances.isEmpty()) return false;
        for (int i = utterances.size() - 1; i >= 0; i--) {
            NextQuestionRequest.Utterance u = utterances.get(i);
            if (!"BOT".equals(u.speaker())) continue;
            String lastQ = u.text() == null ? "" : u.text().toLowerCase();
            if (lastQ.isBlank()) return false;

            // If the previous BOT message looks like a generic probe/fallback, avoid repeating it.
            return lastQ.contains("elaborate")
                || lastQ.contains("tell me more")
                || lastQ.contains("give me more details")
                || lastQ.contains("more details")
                || lastQ.contains("specific example")
                || lastQ.contains("concrete example")
                || lastQ.contains("concrete situation")
                || lastQ.contains("walk me through")
                || lastQ.contains("didn't quite catch")
                || lastQ.contains("didnt quite catch")
                || lastQ.contains("can you repeat")
                || lastQ.contains("what do you mean")
                || lastQ.contains("next prompt from the server")
                || lastQ.contains("staying on what you just said");
        }
        return false;
    }

    private record ManipulationCheck(boolean detected, boolean warn, boolean terminate) {}

    private ManipulationCheck checkManipulation(String lastAnswer, int currentCount) {
        if (lastAnswer == null || lastAnswer.isBlank()) return new ManipulationCheck(false, false, false);
        boolean detected = MANIPULATION_PATTERNS.stream().anyMatch(p -> p.matcher(lastAnswer).find());
        if (!detected) return new ManipulationCheck(false, false, false);
        int newCount = currentCount + 1;
        return new ManipulationCheck(true, newCount <= MANIPULATION_WARN_THRESHOLD, newCount >= MANIPULATION_TERMINATE_THRESHOLD);
    }

    private String llmQuestion(NextQuestionRequest req, String userId) throws Exception {
        String mode = req.getInterviewMode() != null ? req.getInterviewMode() : "L3";
        Map<Integer, String> slotThemes = MODE_SLOT_THEMES.getOrDefault(mode, MODE_SLOT_THEMES.get("L3"));
        String slotTheme = slotThemes.getOrDefault(req.getSlot(),
            "Continue probing technical depth and communication quality relevant to the role.");
        String coveredTopics = extractCoveredTopics(req.getUtterances());

        // Parse candidate profile for difficulty calibration, but override with mode difficulty
        String difficultyInstruction = getDifficultyForMode(mode);
        String levelInstruction = "";
        if (req.getCandidateProfileJson() != null && !req.getCandidateProfileJson().isBlank()) {
            try {
                com.fasterxml.jackson.databind.JsonNode profile =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(req.getCandidateProfileJson());
                String level = profile.path("level").asText("mid");
                int yoe = profile.path("yearsOfExperience").asInt(0);
                levelInstruction = "Candidate is a " + level + " engineer with " + yoe + " years experience — calibrate depth accordingly.";
            } catch (Exception ignored) {}
        }

        // Parse rubric categories to focus on relevant topics
        String rubricFocus = "";
        if (req.getRubricJson() != null && !req.getRubricJson().isBlank()) {
            try {
                com.fasterxml.jackson.databind.JsonNode rubric =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(req.getRubricJson());
                com.fasterxml.jackson.databind.JsonNode cats = rubric.path("categories");
                if (cats.isArray()) {
                    List<String> catLabels = new ArrayList<>();
                    cats.forEach(c -> catLabels.add(c.path("label").asText()));
                    rubricFocus = "Key areas to probe for this role: " + String.join(", ", catLabels);
                }
            } catch (Exception ignored) {}
        }

        String system = String.join("\n",
            "Technical interviewer. Reply with ONE question (1-2 sentences).",
            "React to candidate's answer — reference specifics.",
            "Slot: " + slotTheme,
            levelInstruction,
            rubricFocus.isBlank() ? "" : rubricFocus,
            coveredTopics.isBlank() ? "" : "Covered: " + coveredTopics,
            "Difficulty: " + difficultyInstruction + ". Probe technical knowledge. Natural tone."
        ).stripTrailing();

        String lastAnswer = req.getLastAnswer() != null ? req.getLastAnswer().trim() : "";
        String recent = buildTranscriptContext(req.getUtterances());
        String jdDigest = req.getJdText().substring(0, Math.min(600, req.getJdText().length()));

        StringBuilder user = new StringBuilder();
        if (!lastAnswer.isEmpty()) {
            user.append("Last answer:\n").append(lastAnswer, 0, Math.min(800, lastAnswer.length())).append("\n\n");
        } else {
            user.append("(Opening — no prior answer yet)\n\n");
        }
        if (!recent.isEmpty()) user.append("Recent dialogue:\n").append(recent).append("\n\n");
        user.append("Role: ").append(req.getJdTitle()).append("\n");
        if (req.getResumeSummary() != null && !req.getResumeSummary().isBlank())
            user.append("Resume:\n").append(req.getResumeSummary(), 0, Math.min(600, req.getResumeSummary().length())).append("\n\n");
        if (req.getFocusAreas() != null && !req.getFocusAreas().isBlank())
            user.append("Focus: ").append(req.getFocusAreas()).append("\n");
        user.append("JD:\n").append(jdDigest).append("\n\n");
        user.append(lastAnswer.isEmpty()
            ? "Ask your opening technical question now. One or two sentences, no career narrative."
            : "Ask your next question now. Must follow from their last answer.");

        String result = llmClient.chatQuestionWithSlotAndTracking(system, user.toString(), req.getSlot(), req.getInterviewId(), userId);
        return result.replaceAll("^[\"'\\s]+|[\"'\\s]+$", "");
    }

    private String extractCoveredTopics(List<NextQuestionRequest.Utterance> utterances) {
        if (utterances == null || utterances.isEmpty()) return "";
        return utterances.stream()
            .filter(u -> "BOT".equals(u.speaker()))
            .map(NextQuestionRequest.Utterance::text)
            .collect(Collectors.joining(" | "));
    }

    private String buildTranscriptContext(List<NextQuestionRequest.Utterance> utterances) {
        if (utterances == null || utterances.isEmpty()) return "";
        int total = utterances.size();
        int tailSize = Math.min(10, total);
        List<NextQuestionRequest.Utterance> tail = utterances.subList(total - tailSize, total);
        StringBuilder sb = new StringBuilder();
        tail.forEach(u -> sb.append("BOT".equals(u.speaker()) ? "Interviewer" : "Candidate")
            .append(": ").append(u.text()).append("\n"));
        return sb.toString().trim();
    }

    private String getDifficultyForMode(String mode) {
        return switch (mode) {
            case "SCREENING" -> "easy difficulty";
            case "L1" -> "easy-medium difficulty";
            case "L2" -> "medium difficulty";
            case "L3" -> "medium-hard difficulty";
            case "L4" -> "hard difficulty";
            default -> "medium difficulty";
        };
    }

    private boolean isVagueAnswer(String answer) {
        if (answer == null || answer.isBlank()) return false;
        
        String trimmed = answer.trim().toLowerCase();
        
        // Check for explicit skip/next requests - these should NOT be treated as vague
        String[] skipPatterns = {
            "next question", "skip", "skip this", "move on", "pass", "next",
            "i don't know this", "i dont know this", "not prepared", "can we skip",
            "different questions", "another questions", "can you please different questions",
            "can you please ask different questions", "hello can you please ask different questions"
        };
        
        for (String pattern : skipPatterns) {
            if (trimmed.equals(pattern) || 
                trimmed.startsWith(pattern + " ") || 
                trimmed.startsWith(pattern + ",") ||
                trimmed.endsWith(" " + pattern) ||
                trimmed.contains(" " + pattern + " ")) {
                log.info("Detected skip request: '{}' - allowing progression to next question", trimmed);
                return false; // Allow moving to next question
            }
        }
        
        // Check word count (under 15 words)
        // Voice-to-text answers can be rambling but still short; we only want to probe when it's truly minimal.
        int wordCount = trimmed.split("\\s+").length;
        if (wordCount < 15) {
            log.debug("Answer too short ({} words): '{}'", wordCount, trimmed);
            return true;
        }
        
        // Check for vague keywords
        String[] vaguePatterns = {
            "i don't know", "i dont know", "not sure", "maybe", "yes", "no", 
            "can you repeat", "what do you mean", "i'm not familiar", "im not familiar",
            "i haven't used", "i havent used", "never worked with", "not really",
            "i think so", "probably", "i guess", "sort of", "kind of"
        };
        
        for (String pattern : vaguePatterns) {
            if (trimmed.contains(pattern)) {
                log.debug("Detected vague pattern '{}' in answer: '{}'", pattern, trimmed);
                return true;
            }
        }
        
        return false;
    }

    private String getVagueAnswerProbe() {
        String[] probes = {
            "Can you elaborate on that with a specific example?",
            "Tell me more about your experience with this — what did you actually build?",
            "Walk me through a concrete situation where you used this.",
            "What was the specific problem you were solving, and how did you approach it?",
            "Can you give me more details about the technical implementation?"
        };
        return probes[(int) (Math.random() * probes.length)];
    }

    private String fallbackQuestion(NextQuestionRequest req) {
        int slot = req.getSlot();
        String jdTitle = req.getJdTitle() != null ? req.getJdTitle() : "Target role";
        String lastAnswer = req.getLastAnswer() != null ? req.getLastAnswer().trim() : "";
        boolean hasSubstance = lastAnswer.length() > 25;
        
        // If candidate is asking for next question, force progression
        String lowerAnswer = lastAnswer.toLowerCase();
        if (lowerAnswer.contains("next question") || lowerAnswer.contains("different question") || 
            lowerAnswer.contains("another question") || lowerAnswer.contains("move on")) {
            log.info("Candidate requested next question, forcing progression to slot {}", slot + 1);
            slot = slot + 1; // Force move to next slot
        }

        if (slot == 1 && !hasSubstance)
            return "We'll start technical right away for " + jdTitle + ". Pick one system you've built and walk me through its architecture, trade-offs, and failure handling.";

        if (hasSubstance) {
            List<String> variants = switch (slot) {
                case 2 -> List.of("What was the hardest call you had to make there, and what would you do differently?",
                                  "Who was the customer for that work, and how did you prove it was working?");
                case 3 -> List.of("What broke or got messy in practice, and how did you notice?",
                                  "What did you personally own versus delegate, and how did you keep quality up?");
                case 6 -> List.of("What edge case or failure mode would worry you most, and what's your mitigation?",
                                  "What's the worst thing that actually happened, and what did you change afterward?");
                case 7 -> List.of("What are the main components and where's the riskiest integration?",
                                  "Walk me through the data flow end to end and where you'd expect pain first.");
                default -> List.of("What's one technical idea you'd want a new teammate to understand quickly — where it breaks and when not to use it?",
                                   "What's a concept you'd defend in design review, and what's the main pushback you'd expect?");
            };
            return variants.get(lastAnswer.length() % variants.size());
        }

        return switch (slot) {
            case 2 -> "Walk me through one recent project you owned — problem, what you built, and how you knew it worked.";
            case 3 -> "A situation where you had to trade off speed versus correctness — how did you decide?";
            case 4 -> "Explain one technical idea you'd want a peer to understand quickly — where it shines and where it falls apart.";
            case 5 -> "If that idea had to run in production tomorrow, how would you roll it out and what would you watch first?";
            case 6 -> "An edge case or incident — what breaks first, and how do you harden it?";
            case 7 -> "Sketch a system that fits what we've been discussing — components, data flow, main risk.";
            case 8 -> "A concrete problem in this space — your approach, complexity, tests.";
            case 9 -> "Explain a tricky technical trade-off as you would to a sharp but rushed peer.";
            default -> "A vague requirement — how do you turn it into a concrete technical plan with checkpoints?";
        };
    }
}
