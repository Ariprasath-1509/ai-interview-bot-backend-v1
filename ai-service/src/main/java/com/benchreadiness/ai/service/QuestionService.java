package com.benchreadiness.ai.service;

import com.benchreadiness.ai.dto.NextQuestionRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class QuestionService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(QuestionService.class);

    public record QuestionResult(
            String question,
            boolean manipulationDetected,
            boolean terminateInterview,
            String questionBankId,
            String source,
            boolean isCoding,
            String preferredLanguage,
            String starterCode
    ) {
        public QuestionResult(String question, boolean manipulationDetected, boolean terminateInterview,
                              String questionBankId, String source) {
            this(question, manipulationDetected, terminateInterview, questionBankId, source, false, "python", null);
        }
    }

    private static final int MANIPULATION_WARN_THRESHOLD = 1;
    private static final int MANIPULATION_TERMINATE_THRESHOLD = InjectionGuard.TERMINATE_THRESHOLD;
    private static final long QUESTION_CACHE_TTL_MS = 30 * 60 * 1000; // 30 minutes
    private static final int MAX_CROSS_QUESTIONS_PER_SLOT = 1;
    /** Minimum question slots that must pass between two cross-questions. */
    private static final int CROSS_QUESTION_COOLDOWN_SLOTS = 5;
    private static final int RECENT_BOT_QUESTIONS_FOR_DEDUP = 4;
    /** Dedup against every bot question ever asked in this session, not just recent ones. */
    private static final int ALL_BOT_QUESTIONS_DEDUP = Integer.MAX_VALUE;
    private static final double QUESTION_SIMILARITY_THRESHOLD = 0.62;
    /** Token-saving limits for question-generation prompts (P0/P1). */
    private static final int TRANSCRIPT_TAIL_UTTERANCES = 6;
    private static final int TRANSCRIPT_UTTERANCE_MAX_CHARS = 600;
    private static final int LAST_ANSWER_MAX_CHARS = 800;
    private static final int ROLLING_SUMMARY_SLOT_THRESHOLD = 8;
    private static final int ROLLING_SUMMARY_MAX_CHARS = 2500;
    private static final int QUESTION_BANK_SNIPPET_CHARS = 120;
    private static final int JD_DIGEST_CHARS = 2000;
    private static final int RESUME_DIGEST_CHARS = 1500;

    /**
     * Marks BOT utterances that explain/simplify the current question rather than ask a new one.
     * Explanations intentionally overlap the question's wording, so anything with this prefix is
     * excluded from question dedup and exempt from the sanitize/final-gate similarity checks.
     */
    public static final String EXPLANATION_PREFIX = "In simpler terms: ";

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

    /**
     * Slot themes for ONBOARDING assessments — testing foundational understanding of one stated
     * concept, not role-fit or production experience. Flat (not mode-keyed): onboarding always
     * runs a short, fixed progression regardless of interviewMode's slot-count/duration setting.
     */
    private static final Map<Integer, String> ONBOARDING_SLOT_THEMES = Map.of(
        1, "Core definition — what the concept is, in the candidate's own words, and why it matters in practice.",
        2, "Practical usage — a concrete scenario where they would apply this concept, and how.",
        3, "Common pitfalls — mistakes people commonly make with this concept, and how to avoid them.",
        4, "Compare & contrast — how this concept relates to or differs from a closely adjacent one.",
        5, "Applied problem — a small scenario question that requires using the concept to reach an answer."
    );

    /**
     * Curated real interview questions per mode and slot.
     * Used by progressQuestionAvoidingRecent so skip/advance responses are proper questions,
     * not raw theme directive text.
     */
    private static final Map<String, Map<Integer, String>> MODE_SLOT_QUESTIONS = Map.of(
        "SCREENING", Map.of(
            1, "Walk me through the most recent system you built or contributed to significantly — what problem it solved, your stack, and the scale it operated at.",
            2, "Pick the one backend technology in this role's stack you use most — explain how it works under the hood and a case where you'd avoid it.",
            3, "Given a list of integers, write a function that returns every pair summing to a target value — walk me through your approach and its time complexity.",
            4, "Your API response time jumped from 200 ms to 2 seconds overnight — how would you explain what's happening to a product manager, and what's your first diagnostic step?",
            5, "What specifically draws you to a backend role like this one, and what's the one technical area you most want to grow in over the next year?"
        ),
        "L1", Map.of(
            1, "Walk me through a backend service you shipped recently — what problem it solved, what you owned specifically, and the one decision you'd make differently.",
            2, "Pick the core backend technology in the JD you know best — explain its concurrency model and a real scenario where it caused you trouble.",
            3, "Write a function that returns the first non-repeating character in a string — walk me through your approach, edge cases, and complexity.",
            4, "How do you decide when code is production-ready — what's your personal quality bar, and what corners have you cut and later regretted?",
            5, "Describe your testing strategy for a new REST endpoint — what you'd unit test, what you'd integration test, and where you'd stop.",
            6, "Tell me about a technology or pattern you learned in the last six months and applied in real work — what drove you to pick it up?",
            7, "Walk me through a code review where you either gave or received feedback that genuinely changed how you write code."
        ),
        "L2", Map.of(
            1, "Describe a multi-service system you've worked on — how services communicate, where ownership boundaries sit, and how it behaves when a downstream is down.",
            2, "Tell me about a design decision where you traded consistency for availability or vice versa — what tipped the scale and what problems followed?",
            3, "Walk me through a production incident you owned — what you saw first, how you isolated the root cause, and what you changed to prevent recurrence.",
            4, "How do you handle shared mutable state across services — what patterns have you used and where did they break down in practice?",
            5, "Describe a performance bottleneck you traced and fixed — tooling, root cause, and what the fix cost you elsewhere.",
            6, "Walk me through your incident playbook — how you detect, communicate, root-cause, and formally close out a production outage.",
            7, "Which design pattern have you applied most deliberately in production — why you chose it, and a case where you considered it but opted for something simpler?",
            8, "Describe a third-party integration that caused you production pain — what the failure mode was and how you made your service resilient to it."
        ),
        "L3", Map.of(
            1, "Walk me through a system you architected from scratch — requirements, key component decisions, and the two choices you'd revisit with hindsight.",
            2, "Design a distributed cache with strong consistency guarantees — explain the core trade-offs and how you'd handle a network partition.",
            3, "Describe your approach to preventing cascading failures — circuit breakers, bulkheads, timeouts — with a concrete production example.",
            4, "Walk me through how you profiled and optimized a system hitting its performance ceiling — tooling, root cause, and what the fix cost elsewhere.",
            5, "How do you choose between a relational database, a document store, and a key-value cache for different parts of the same system?",
            6, "What does your observability stack look like in production — metrics, logging, tracing — and what alert fired last that actually mattered?",
            7, "Walk me through a threat model for a public API you've built — what attack surfaces you identified and how you hardened them.",
            8, "Describe a time you drove a significant architectural change — how you built consensus, managed migration risk, and handled rollback.",
            9, "You need to migrate a high-traffic monolith to microservices with zero downtime — what's your sequencing strategy?",
            10, "A vague product requirement arrives that looks technically risky — walk me through how you turn it into a scoped, low-risk implementation plan."
        ),
        "L4", Map.of(
            1, "Design a real-time event pipeline that processes 1M events per second with at-least-once delivery — walk me through the architecture and failure recovery.",
            2, "Compare the saga pattern to two-phase commit for cross-service transactions in a high-throughput order system — defend your choice under real load.",
            3, "Walk me through a chaos engineering exercise you designed or ran — what hypotheses you tested, what you found, and what you changed.",
            4, "Describe a cross-team technical alignment problem you solved — competing priorities, key stakeholders, and how you drove an actual decision.",
            5, "A VP wants to adopt a new database for the core platform — what's your evaluation framework and how do you manage the risk of being wrong?",
            6, "What does your team's technical roadmap look like for the next 18 months and how do you balance it against immediate product delivery pressure?",
            7, "How do you establish and enforce technical standards across multiple teams without creating bureaucratic drag that slows delivery?",
            8, "Walk me through a technology bet you made that didn't pay off — what you evaluated, what went wrong, and what you'd do differently.",
            9, "How do you identify high-potential engineers and grow them toward staff-level impact — what does your actual approach look like in practice?",
            10, "A critical business initiative depends on a technical decision you believe is wrong — how do you handle it?"
        )
    );

    /** DevOps/SRE/platform variant of the curated pool — same mode/slot structure as MODE_SLOT_QUESTIONS. */
    private static final Map<String, Map<Integer, String>> DEVOPS_MODE_SLOT_QUESTIONS = Map.of(
        "SCREENING", Map.of(
            1, "Walk me through the infrastructure you've supported most recently — what it ran on, how deployments worked, and the scale it operated at.",
            2, "Pick the one tool in this role's stack you use most — containers, IaC, or your CI system — explain how it works under the hood and a case where you'd avoid it.",
            3, "You need a script that finds all log files over 1 GB across a fleet of servers and archives them safely — walk me through your approach and what could go wrong.",
            4, "Production deployments started failing overnight — how would you explain what's happening to a product manager, and what's your first diagnostic step?",
            5, "What specifically draws you to a DevOps role like this one, and what's the one technical area you most want to grow in over the next year?"
        ),
        "L1", Map.of(
            1, "Walk me through a pipeline or automation you shipped recently — what problem it solved, what you owned specifically, and the one decision you'd make differently.",
            2, "Explain the difference between a container and a virtual machine, and describe a real scenario where containerization caused you trouble.",
            3, "Write a script that parses a log file and reports the top five most frequent error messages — walk me through your approach and edge cases.",
            4, "How do you decide when a CI/CD pipeline change is production-ready — what's your quality bar, and what corners have you cut and later regretted?",
            5, "Describe how you validate an infrastructure change before rolling it out — what you test, how you stage it, and where you'd stop.",
            6, "Tell me about a tool or practice you learned in the last six months and applied in real work — what drove you to pick it up?",
            7, "Walk me through a time you worked with developers to fix a deployment or environment issue — how did you split the work?"
        ),
        "L2", Map.of(
            1, "Describe the environments and deployment topology you've managed — how code moves from commit to production and what happens when a stage fails.",
            2, "Tell me about a decision where you traded deployment speed for safety or vice versa — what tipped the scale and what problems followed?",
            3, "Walk me through a production incident you owned — what you saw first, how you isolated the root cause, and what you changed to prevent recurrence.",
            4, "How do you manage infrastructure state and configuration drift — what patterns have you used and where did they break down in practice?",
            5, "Describe a capacity or performance bottleneck you traced in infrastructure — tooling, root cause, and what the fix cost you elsewhere.",
            6, "Walk me through your incident playbook — how you detect, communicate, root-cause, and formally close out a production outage.",
            7, "Which deployment strategy have you applied most deliberately — blue-green, canary, rolling — why you chose it, and a case where you opted for something simpler?",
            8, "Describe a third-party or cloud service integration that caused you production pain — what the failure mode was and how you made your systems resilient to it."
        ),
        "L3", Map.of(
            1, "Walk me through a platform or infrastructure architecture you designed from scratch — requirements, key component decisions, and the two choices you'd revisit with hindsight.",
            2, "Design a highly available container platform across multiple availability zones — explain the core trade-offs and how you'd handle a zone failure.",
            3, "Describe your approach to preventing cascading failures in infrastructure — health checks, autoscaling limits, circuit breakers — with a concrete production example.",
            4, "Walk me through how you profiled and fixed an infrastructure performance ceiling — CPU, network, or I/O — tooling, root cause, and what the fix cost elsewhere.",
            5, "How do you design log, metric, and trace storage for a large fleet — retention, cost, and query performance trade-offs?",
            6, "What does your observability stack look like in production — metrics, logging, tracing — and what alert fired last that actually mattered?",
            7, "Walk me through how you've hardened infrastructure — secrets management, network policies, least-privilege access — and the gap you'd fix first today.",
            8, "Describe a time you drove a significant infrastructure change — how you built consensus, managed migration risk, and handled rollback.",
            9, "You need to migrate a high-traffic system to new infrastructure with zero downtime — what's your sequencing strategy?",
            10, "A vague reliability requirement arrives — \"make it more stable\" — walk me through how you turn it into a scoped, measurable plan."
        ),
        "L4", Map.of(
            1, "Design a multi-region deployment platform serving hundreds of services with automated failover — walk me through the architecture and failure recovery.",
            2, "Compare running your own container orchestration against managed services for a large organization — defend your choice under real cost and reliability constraints.",
            3, "Walk me through a chaos engineering exercise you designed or ran — what hypotheses you tested, what you found, and what you changed.",
            4, "Describe a cross-team reliability or platform alignment problem you solved — competing priorities, key stakeholders, and how you drove an actual decision.",
            5, "A VP wants to adopt a new cloud provider or major platform — what's your evaluation framework and how do you manage the risk of being wrong?",
            6, "What does your platform roadmap look like for the next 18 months and how do you balance it against immediate delivery pressure?",
            7, "How do you establish and enforce infrastructure standards across multiple teams without creating bureaucratic drag that slows delivery?",
            8, "Walk me through a technology bet you made that didn't pay off — what you evaluated, what went wrong, and what you'd do differently.",
            9, "How do you identify high-potential engineers and grow them toward staff-level impact — what does your actual approach look like in practice?",
            10, "A critical business initiative depends on a technical decision you believe is wrong — how do you handle it?"
        )
    );

    /** Role-neutral curated pool used when the JD doesn't clearly match a dedicated family. */
    private static final Map<String, Map<Integer, String>> GENERIC_MODE_SLOT_QUESTIONS = Map.of(
        "SCREENING", Map.of(
            1, "Walk me through the most recent system you built or supported significantly — what problem it solved, your tooling, and the scale it operated at.",
            2, "Pick the one technology in this role's stack you use most — explain how it works under the hood and a case where you'd avoid it.",
            3, "Describe a small technical problem you solved recently end to end — your approach, what you checked, and how you verified the result.",
            4, "Something you own slowed down or started failing overnight — how would you explain what's happening to a product manager, and what's your first diagnostic step?",
            5, "What specifically draws you to this role, and what's the one technical area you most want to grow in over the next year?"
        ),
        "L1", Map.of(
            1, "Walk me through something you shipped recently — what problem it solved, what you owned specifically, and the one decision you'd make differently.",
            2, "Pick the core technology in the JD you know best — explain how it works internally and a real scenario where it caused you trouble.",
            3, "Describe how you'd turn a repetitive manual task from your work into an automated solution — approach, edge cases, and how you'd verify it.",
            4, "How do you decide when your work is production-ready — what's your personal quality bar, and what corners have you cut and later regretted?",
            5, "Describe your approach to testing or validating a change before it reaches users — what you check and where you'd stop.",
            6, "Tell me about a technology or practice you learned in the last six months and applied in real work — what drove you to pick it up?",
            7, "Walk me through a review where you either gave or received feedback that genuinely changed how you work."
        ),
        "L2", Map.of(
            1, "Describe a multi-component system you've worked on — how the pieces communicate, where ownership boundaries sit, and how it behaves when one part is down.",
            2, "Tell me about a design decision where you traded one quality for another — speed versus correctness, cost versus reliability — what tipped the scale and what problems followed?",
            3, "Walk me through a production issue you owned — what you saw first, how you isolated the root cause, and what you changed to prevent recurrence.",
            4, "How do you manage shared state or configuration across components — what patterns have you used and where did they break down in practice?",
            5, "Describe a performance bottleneck you traced and fixed — tooling, root cause, and what the fix cost you elsewhere.",
            6, "Walk me through your incident playbook — how you detect, communicate, root-cause, and formally close out a production issue.",
            7, "Which recurring pattern or practice have you applied most deliberately in production — why you chose it, and a case where you opted for something simpler?",
            8, "Describe an external dependency that caused you production pain — what the failure mode was and how you made your system resilient to it."
        ),
        "L3", Map.of(
            1, "Walk me through a system you designed from scratch — requirements, key decisions, and the two choices you'd revisit with hindsight.",
            2, "Design a system in your domain that must stay available through partial failures — explain the core trade-offs and how you'd handle losing a critical component.",
            3, "Describe your approach to preventing small failures from becoming big ones — with a concrete production example.",
            4, "Walk me through how you profiled and optimized something hitting its performance ceiling — tooling, root cause, and what the fix cost elsewhere.",
            5, "How do you decide where data or state should live in a system — the storage and caching trade-offs you weigh?",
            6, "What does your monitoring or quality-signal setup look like — and what alert or signal fired last that actually mattered?",
            7, "Walk me through how you think about security in your work — what risks you check for and how you harden against them.",
            8, "Describe a time you drove a significant technical change — how you built consensus, managed risk, and handled rollback.",
            9, "You need to migrate a critical system to a new platform with zero downtime — what's your sequencing strategy?",
            10, "A vague requirement arrives that looks technically risky — walk me through how you turn it into a scoped, low-risk plan."
        ),
        "L4", Map.of(
            1, "Design a system in your domain that must handle a tenfold traffic increase within a year — walk me through the architecture and how you'd stage the investment.",
            2, "Compare two competing architectural approaches you've had to choose between at scale — defend your choice under real load and cost constraints.",
            3, "Walk me through a chaos engineering exercise you designed or ran — what hypotheses you tested, what you found, and what you changed.",
            4, "Describe a cross-team technical alignment problem you solved — competing priorities, key stakeholders, and how you drove an actual decision.",
            5, "A VP wants to adopt a new core technology for the platform — what's your evaluation framework and how do you manage the risk of being wrong?",
            6, "What does your team's technical roadmap look like for the next 18 months and how do you balance it against immediate product delivery pressure?",
            7, "How do you establish and enforce technical standards across multiple teams without creating bureaucratic drag that slows delivery?",
            8, "Walk me through a technology bet you made that didn't pay off — what you evaluated, what went wrong, and what you'd do differently.",
            9, "How do you identify high-potential engineers and grow them toward staff-level impact — what does your actual approach look like in practice?",
            10, "A critical business initiative depends on a technical decision you believe is wrong — how do you handle it?"
        )
    );

    /**
     * Generic fallback questions for ONBOARDING assessments — used only when the LLM path fails
     * (thin-answer/dedup progression tries the LLM first). "%s" is the stated concept/topic name.
     */
    private static final List<String> ONBOARDING_FALLBACK_QUESTIONS = List.of(
        "In your own words, what is %s and why does it matter in day-to-day work?",
        "Walk me through a simple example of using %s — what does it look like in practice?",
        "What's a common mistake beginners make with %s, and how would you avoid it?",
        "How does %s compare to a related concept you know — what's the key difference?",
        "Here's a small scenario: [describe a situation relevant to %s]. How would you approach it?"
    );

    /** Last-resort pool once curated + slot-fallback questions are exhausted (long, extended-timer
     *  interviews). Deliberately large so genuine collisions are rare before falling through to the
     *  frame/angle synthesizer below. */
    private static final List<String> LAST_RESORT_ALTERNATES = List.of(
        "Describe a technical decision you reversed after production feedback — what signal triggered the change?",
        "Tell me about a time you simplified an over-engineered solution — what did you remove and why?",
        "What's a monitoring or observability gap you discovered only after an incident, and how did you close it?",
        "How would you onboard a new engineer to the most complex part of a system you've built?",
        "Walk me through how you'd debug a memory leak in a long-running service — tools, steps, root cause approach.",
        "Describe the most brittle part of a system you've maintained and what you did to make it more resilient.",
        "What's the hardest trade-off you've made between developer experience and system performance?",
        "Tell me about a time you disagreed with a technical decision — how did you handle it?",
        "What's a piece of legacy code you inherited and how did you approach improving it?",
        "Describe a time you had to make a call with incomplete information — what did you do?",
        "What's the most useful piece of feedback you've received on your code, and how did it change your approach?",
        "Walk me through how you'd approach reviewing a large, unfamiliar pull request.",
        "Tell me about a production incident you were involved in — what was your role and what did you learn?",
        "What's a technical skill you've deliberately worked on improving in the last year, and how?",
        "Describe how you'd explain a complex technical concept to a non-technical stakeholder.",
        "What's a time estimate you got wrong — what threw it off, and what would you do differently?",
        "How do you decide when to write a test versus when it's not worth the time?",
        "Tell me about a dependency or third-party service that caused you trouble — how did you handle it?",
        "What's a part of your current stack you'd change if you could, and why?",
        "Describe a time you had to push back on scope to hit a deadline."
    );

    /** Rotating sentence frames for synthesizing a genuinely fresh fallback question once even
     *  {@link #LAST_RESORT_ALTERNATES} is exhausted. "%s" takes an angle from {@link #LAST_RESORT_ANGLES}. */
    private static final List<String> LAST_RESORT_FRAMES = List.of(
        "Let's go deeper on %s — walk me through a specific example from your experience.",
        "Thinking about %s: what's a mistake you've seen made there, and what would you do instead?",
        "How do you approach %s in your current role — what does good look like?",
        "Tell me about a time %s mattered more than you expected — what happened?",
        "What's your mental checklist for %s, and where does it usually break down in practice?"
    );

    /** Rotating engineering angles paired with {@link #LAST_RESORT_FRAMES}. */
    private static final List<String> LAST_RESORT_ANGLES = List.of(
        "code review quality", "on-call and incident response", "technical debt prioritization",
        "cross-team collaboration", "performance under load", "backward compatibility",
        "rollout and rollback strategy", "documentation and knowledge sharing",
        "estimating and scoping work", "mentoring and unblocking teammates"
    );

    /** Designated slot for the primary coding exercise per interview mode. */
    private static final Map<String, Integer> CODING_SLOT_BY_MODE = Map.of(
        "SCREENING", 3,
        "L1", 3,
        "L2", 8,
        "L3", 8,
        "L4", 8
    );

    /** At most 2 coding questions per interview — a 2nd is only offered if the 1st was wrong/partial. */
    private static final int MAX_CODING_QUESTIONS_PER_INTERVIEW = 2;
    /** Slots after the primary coding slot before offering the (conditional) easier retry. */
    private static final int SECOND_CODING_SLOT_OFFSET = 2;

    /** Checklist knockout questions (mandatory, from a client screening checklist) are given a
     *  deadline slot each so they're guaranteed to be asked rather than left to chance — the
     *  Nth knockout question (0-indexed) must be asked by slot KNOCKOUT_FIRST_DEADLINE_SLOT + N * KNOCKOUT_SLOT_SPACING. */
    private static final int KNOCKOUT_FIRST_DEADLINE_SLOT = 4;
    private static final int KNOCKOUT_SLOT_SPACING = 4;

    private final LlmClient llmClient;
    private final QuestionCacheService cacheService;
    private final ConcurrentHashMap<String, CachedQuestionResult> requestCache = new ConcurrentHashMap<>();

    private record CachedQuestionResult(QuestionResult result, long createdAtMs) {}

    public QuestionService(LlmClient llmClient, QuestionCacheService cacheService) {
        this.llmClient = llmClient;
        this.cacheService = cacheService;
    }

    public QuestionResult getNextQuestion(NextQuestionRequest req, String userId) {
        String requestCacheKey = buildRequestCacheKey(req);
        QuestionResult cachedResult = getCachedResult(requestCacheKey, req);
        if (cachedResult != null) {
            return cachedResult;
        }

        // Check for manipulation
        ManipulationCheck check = checkManipulation(req.getLastAnswer(), req.getManipulationCount());

        if (check.terminate()) {
            QuestionResult result = new QuestionResult(
                "This interview is being terminated. Multiple attempts to manipulate the AI interviewer have been detected and flagged for review.",
                true, true, null, "AI_GENERATED"
            );
            return finalizeAndCache(requestCacheKey, req, result);
        }

        if (check.warn()) {
            QuestionResult result = new QuestionResult(
                "Please answer the interview questions directly. Attempts to influence the AI or manipulate scores are not allowed and have been noted.",
                true, false, null, "AI_GENERATED"
            );
            return finalizeAndCache(requestCacheKey, req, result);
        }

        if (isSkipOrAdvanceRequest(req.getLastAnswer())) {
            log.info("Skip/advance request detected — progressing without probe (slot={})", req.getSlot());
            QuestionResult result = new QuestionResult(
                progressQuestionAvoidingRecent(req, req.getSlot()),
                false, false, null, "AI_GENERATED"
            );
            return finalizeAndCache(requestCacheKey, req, result);
        }

        if (isClarificationRequest(req.getLastAnswer())) {
            log.info("Clarification request detected — explaining last question in simpler terms (slot={})", req.getSlot());
            QuestionResult result = new QuestionResult(
                rephraseLastQuestion(req, userId),
                false, false, null, "AI_GENERATED"
            );
            return finalizeAndCache(requestCacheKey, req, result);
        }

        // Priority 1: Check if custom questions are available
        if (req.getCustomQuestionsJson() != null && !req.getCustomQuestionsJson().isBlank()) {
            try {
                QuestionResult customQuestionResult = selectFromCustomQuestions(req);
                if (customQuestionResult != null) {
                    return finalizeAndCache(requestCacheKey, req, customQuestionResult);
                }
            } catch (Exception e) {
                log.warn("Failed to select from custom questions, falling back to question bank or AI: {}", e.getMessage());
            }
        }

        // Priority 2: Check if question bank questions are available
        if (req.getQuestionBankQuestionsJson() != null && !req.getQuestionBankQuestionsJson().isBlank()) {
            try {
                QuestionResult questionBankResult = selectFromQuestionBank(req, userId);
                if (questionBankResult != null) {
                    return finalizeAndCache(requestCacheKey, req, questionBankResult);
                }
            } catch (Exception e) {
                log.warn("Failed to select from question bank, falling back to AI generation: {}", e.getMessage());
            }
        }

        // Priority 3: Normal AI generation flow
        if (llmClient.isConfigured()) {
            try {
                // Check for vague/short answers first
                // IMPORTANT: don't get stuck in probe loops. Allow one probe, then move forward deterministically.
                if (isVagueAnswer(req.getLastAnswer()) || isLowSubstanceAnswer(req.getLastAnswer())) {
                    int recentProbeCount = countRecentProbeQuestions(req.getUtterances());
                    if (recentProbeCount >= 1 || lastQuestionWasProbe(req.getUtterances())
                        || countSimilarRepeatedBotQuestions(req.getUtterances()) >= 1) {
                        // Probe budget spent — ask the LLM for a fresh on-topic question instead of
                        // jumping straight to the curated pool; curated stays as the error fallback.
                        String progressed;
                        try {
                            progressed = llmQuestion(req, userId, true);
                            List<String> allBotQs = extractRecentBotQuestions(req.getUtterances(), ALL_BOT_QUESTIONS_DEDUP);
                            if (isQuestionSimilarToRecent(progressed, allBotQs)) {
                                progressed = progressQuestionAvoidingAll(req);
                            }
                        } catch (Exception e) {
                            log.warn("Thin-answer LLM progression failed, using curated pool: {}", e.getMessage());
                            progressed = progressQuestionAvoidingRecent(req, req.getSlot());
                        }
                        QuestionResult result = new QuestionResult(progressed, false, false, null, "AI_GENERATED");
                        return finalizeAndCache(requestCacheKey, req, result);
                    }
                    QuestionResult result = new QuestionResult(getVagueAnswerProbe(), false, false, null, "AI_GENERATED");
                    return finalizeAndCache(requestCacheKey, req, result);
                }
                
                // Check cache for first question — skipped whenever a resume is present, since the
                // opening question is personalized per-candidate from it (see llmQuestion below);
                // the cache has no candidate dimension, so serving a hit would hand one candidate's
                // resume-tailored question to every other candidate sharing the same JD/mode.
                boolean hasResume = req.getResumeSummary() != null && !req.getResumeSummary().isBlank();
                if (req.getSlot() == 1 && !hasResume && (req.getLastAnswer() == null || req.getLastAnswer().isBlank())) {
                    String firstQuestionCacheHit = cacheService.getCachedFirstQuestion(
                        req.getJdTitle(), req.getJdText(), req.getFocusAreas(), req.getInterviewMode());
                    if (firstQuestionCacheHit != null) {
                        QuestionResult result = new QuestionResult(firstQuestionCacheHit, false, false, null, "CACHED",
                            resolveIsCoding(firstQuestionCacheHit, null),
                            inferLanguageFromContext(firstQuestionCacheHit, req.getJdTitle()), null);
                        return finalizeAndCache(requestCacheKey, req, result);
                    }
                }
                
                String question = llmQuestion(req, userId);
                List<String> allBot = extractRecentBotQuestions(req.getUtterances(), ALL_BOT_QUESTIONS_DEDUP);
                if (isQuestionSimilarToRecent(question, allBot)) {
                    log.info("AI question duplicates a past bot question — advancing slot theme");
                    question = progressQuestionAvoidingAll(req);
                }

                // Cache first question if this was slot 1 — never for a resume-personalized question
                if (req.getSlot() == 1 && !hasResume && (req.getLastAnswer() == null || req.getLastAnswer().isBlank())) {
                    cacheService.cacheFirstQuestion(
                        req.getJdTitle(), req.getJdText(), req.getFocusAreas(), req.getInterviewMode(), question);
                }

                QuestionResult result = new QuestionResult(question, false, false, null, "AI_GENERATED",
                    resolveIsCoding(question, null), inferLanguageFromContext(question, req.getJdTitle()), null);
                return finalizeAndCache(requestCacheKey, req, result);
            } catch (Exception e) {
                log.warn("Claude failed, falling back to heuristic: {}", e.getMessage());
            }
        } else {
            log.warn("LLM provider not configured — falling back to heuristic");
        }
        QuestionResult fallback = new QuestionResult(fallbackQuestion(req), false, false, null, "FALLBACK",
            false, "python", null);
        return finalizeAndCache(requestCacheKey, req, fallback);
    }

    private String buildRequestCacheKey(NextQuestionRequest req) {
        String interviewId = req.getInterviewId() != null ? req.getInterviewId() : "unknown";
        String slot = String.valueOf(req.getSlot());
        String manipulationCount = String.valueOf(req.getManipulationCount());
        String answer = req.getLastAnswer() != null ? req.getLastAnswer().trim() : "";

        String tailContext = "";
        if (req.getUtterances() != null && !req.getUtterances().isEmpty()) {
            int size = req.getUtterances().size();
            int start = Math.max(0, size - 4);
            tailContext = req.getUtterances().subList(start, size).stream()
                .map(u -> u.speaker() + ":" + u.text())
                .collect(Collectors.joining("|"));
        }

        return interviewId + "|" + slot + "|" + manipulationCount + "|" + answer + "|" + tailContext;
    }

    private QuestionResult getCachedResult(String key, NextQuestionRequest req) {
        CachedQuestionResult cached = requestCache.get(key);
        if (cached == null) return null;
        long age = System.currentTimeMillis() - cached.createdAtMs();
        if (age > QUESTION_CACHE_TTL_MS) {
            requestCache.remove(key);
            return null;
        }
        return applyCodingSlotPolicy(req, cached.result());
    }

    private QuestionResult finalizeAndCache(String key, NextQuestionRequest req, QuestionResult result) {
        QuestionResult sanitized = sanitizeQuestionResult(req, result);
        QuestionResult finalized = applyCodingSlotPolicy(req, sanitized);
        finalized = applyKnockoutQuestionPolicy(req, finalized);

        // Hard final gate: one last check against ALL bot questions before sending to candidate.
        // Explanations are exempt — they intentionally restate the question they simplify.
        if (finalized != null && finalized.question() != null
                && !finalized.question().startsWith(EXPLANATION_PREFIX)) {
            List<String> allBot = extractRecentBotQuestions(req.getUtterances(), ALL_BOT_QUESTIONS_DEDUP);
            if (isQuestionSimilarToRecent(finalized.question(), allBot)) {
                log.warn("Final gate: question still duplicates session history after sanitize — forcing fresh pick (source={})", finalized.source());
                String fresh = progressQuestionAvoidingAll(req);
                finalized = copyResult(finalized, fresh, finalized.isCoding());
            }
        }

        cacheResult(key, finalized);
        return finalized;
    }

    /** Dedupe repeats, fix isCoding flags, and progress when the model re-asks the same thing. */
    private QuestionResult sanitizeQuestionResult(NextQuestionRequest req, QuestionResult result) {
        if (result == null) return null;
        String question = result.question();
        if (question == null || question.isBlank()) return result;

        // Explanations deliberately mirror the question they simplify — never dedup them away
        if (question.startsWith(EXPLANATION_PREFIX)) return result;

        // Check against ALL previously asked bot questions, not just the last few
        List<String> allBot = extractRecentBotQuestions(req.getUtterances(), ALL_BOT_QUESTIONS_DEDUP);
        if (isQuestionSimilarToRecent(question, allBot)) {
            log.warn("Sanitize: question repeats a past bot question — forcing progression (source={})", result.source());
            question = progressQuestionAvoidingAll(req);
            return copyResult(result, question, false);
        }

        boolean isCoding = isProgrammingEnabled(req)
            ? resolveIsCoding(question, bankQuestionTypeForResult(req, result))
            : false;
        if (isCoding != result.isCoding()) {
            log.info("Sanitize: corrected isCoding {} -> {} for source={}", result.isCoding(), isCoding, result.source());
        }
        return copyResult(result, question, isCoding);
    }

    private String bankQuestionTypeForResult(NextQuestionRequest req, QuestionResult result) {
        if (result.questionBankId() == null || result.questionBankId().isBlank()) return null;
        if (req.getQuestionBankQuestionsJson() == null || req.getQuestionBankQuestionsJson().isBlank()) return null;
        try {
            com.fasterxml.jackson.databind.JsonNode arr =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(req.getQuestionBankQuestionsJson());
            if (!arr.isArray()) return null;
            for (com.fasterxml.jackson.databind.JsonNode q : arr) {
                if (result.questionBankId().equals(q.path("id").asText())) {
                    String t = q.path("questionType").asText("");
                    return t.isBlank() ? null : t;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void cacheResult(String key, QuestionResult result) {
        requestCache.put(key, new CachedQuestionResult(result, System.currentTimeMillis()));
        if (requestCache.size() > 5000) {
            clearExpiredRequestCache();
        }
    }

    private void clearExpiredRequestCache() {
        long now = System.currentTimeMillis();
        requestCache.entrySet().removeIf(entry -> (now - entry.getValue().createdAtMs()) > QUESTION_CACHE_TTL_MS);
    }

    private boolean lastQuestionWasProbe(List<NextQuestionRequest.Utterance> utterances) {
        if (utterances == null || utterances.isEmpty()) return false;
        for (int i = utterances.size() - 1; i >= 0; i--) {
            NextQuestionRequest.Utterance u = utterances.get(i);
            if (!"BOT".equals(u.speaker())) continue;
            String lastQ = u.text() == null ? "" : u.text().toLowerCase();
            if (lastQ.isBlank()) return false;

            // If the previous BOT message looks like a generic probe/fallback, avoid repeating it.
            if (lastQ.contains("elaborate")
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
                || lastQ.contains("staying on what you just said")
                || lastQ.contains("can you explain")
                || lastQ.contains("how does")
                || lastQ.contains("how do")
                || lastQ.contains("what is partition")
                || lastQ.contains("what are topics")) {
                return true;
            }
            List<String> prior = extractRecentBotQuestions(utterances, 3);
            if (prior.size() >= 2 && isQuestionSimilar(prior.get(0), prior.get(1))) {
                return true;
            }
            return false;
        }
        return false;
    }

    private record ManipulationCheck(boolean detected, boolean warn, boolean terminate) {}

    private ManipulationCheck checkManipulation(String lastAnswer, int currentCount) {
        if (lastAnswer == null || lastAnswer.isBlank()) return new ManipulationCheck(false, false, false);
        boolean detected = InjectionGuard.isInjection(lastAnswer);
        if (!detected) return new ManipulationCheck(false, false, false);
        int newCount = currentCount + 1;
        return new ManipulationCheck(true, newCount <= MANIPULATION_WARN_THRESHOLD, newCount >= MANIPULATION_TERMINATE_THRESHOLD);
    }

    /**
     * For timer-based interviews, slots can go well beyond the fixed theme set.
     * Cycle back through themes with a "deeper dive" framing so questions stay fresh.
     */
    private String resolveSlotTheme(String mode, int slot) {
        Map<Integer, String> slotThemes = MODE_SLOT_THEMES.getOrDefault(mode, MODE_SLOT_THEMES.get("L3"));
        if (slotThemes.containsKey(slot)) return slotThemes.get(slot);

        // Extended slot: cycle through the theme list with a depth marker so the AI knows
        // to go deeper rather than re-opening the same area at the same level.
        int maxDefined = slotThemes.size();
        int cycledSlot = ((slot - 1) % maxDefined) + 1;
        String baseTheme = slotThemes.getOrDefault(cycledSlot, "Technical depth");
        int cycle = (slot - 1) / maxDefined; // 0=first pass, 1=second pass (deeper), ...
        String depthLabel = switch (cycle) {
            case 0 -> baseTheme;
            case 1 -> "Deeper dive — " + baseTheme.toLowerCase() + " Push for edge cases, failure modes, and production war stories.";
            case 2 -> "Expert probe — " + baseTheme.toLowerCase() + " Focus on trade-offs the candidate would defend under push-back.";
            default -> "Mastery — " + baseTheme.toLowerCase() + " Challenge assumptions and probe cross-cutting concerns.";
        };
        return depthLabel;
    }

    private String llmQuestion(NextQuestionRequest req, String userId) throws Exception {
        return llmQuestion(req, userId, false);
    }

    private String llmQuestion(NextQuestionRequest req, String userId, boolean thinAnswerMode) throws Exception {
        if (req.isOnboarding()) {
            return llmOnboardingQuestion(req, userId, thinAnswerMode);
        }
        String mode = req.getInterviewMode() != null ? req.getInterviewMode() : "L3";
        String slotTheme = resolveSlotTheme(mode, req.getSlot());
        String coveredTopics = extractCoveredTopics(req.getUtterances());
        List<String> rubricLabels = extractRubricLabels(req.getRubricJson());
        String targetSkill = pickTargetSkill(req.getSlot(), rubricLabels, coveredTopics);
        String coverageHint = buildCoverageHint(rubricLabels, coveredTopics);

        // Difficulty: explicit per-round selection wins; otherwise derived from mode
        String difficultyInstruction = resolveDifficulty(req);
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

        // Parse rubric categories to focus on relevant topics — include each category's
        // "what to probe" description (for checklist-based rubrics this is the client's own
        // "what good looks like" text) so questions target the exact evidence being scored,
        // not just the category name.
        String rubricFocus = "";
        if (req.getRubricJson() != null && !req.getRubricJson().isBlank()) {
            try {
                com.fasterxml.jackson.databind.JsonNode rubric =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(req.getRubricJson());
                com.fasterxml.jackson.databind.JsonNode cats = rubric.path("categories");
                if (cats.isArray()) {
                    List<String> catFocus = new ArrayList<>();
                    cats.forEach(c -> {
                        String label = c.path("label").asText();
                        String description = c.path("description").asText("");
                        catFocus.add(description.isBlank() ? label : label + " (" + description + ")");
                    });
                    rubricFocus = "Key areas to probe for this role:\n" + catFocus.stream()
                        .map(f -> "- " + f).collect(java.util.stream.Collectors.joining("\n"));
                }
            } catch (Exception ignored) {}
        }

        // Build list of ALL previously asked bot questions for the "never repeat" block
        List<String> allPastBotQuestions = extractRecentBotQuestions(req.getUtterances(), ALL_BOT_QUESTIONS_DEDUP);
        String neverRepeatBlock = allPastBotQuestions.isEmpty()
            ? "None yet."
            : allPastBotQuestions.stream()
                .map(q -> "- " + truncateText(q, 120))
                .collect(Collectors.joining("\n"));

        String system =
            "You are a precise, conversational technical interviewer. Your output must be exactly ONE question (1-2 sentences max).\n" +
            "Your PRIMARY anchor is the Job Description and the candidate's resume — not whatever project they last mentioned.\n" +
            "\n" +
            "CURRENT INTERVIEW MATRIX CONTEXT:\n" +
            "- Current Slot Theme: " + slotTheme + "\n" +
            "- Target Skill Priority: " + (targetSkill.isBlank() ? "General technical depth" : targetSkill) + "\n" +
            "- Coverage Strategy: " + (coverageHint.isBlank() ? "Probe technical depth" : coverageHint) + "\n" +
            "- Seniority Level Guardrails: " + (levelInstruction.isBlank() ? "Calibrate to role level" : levelInstruction) + "\n" +
            "- Evaluation Rubric Focus: " + (rubricFocus.isBlank() ? "Role-relevant technical areas" : rubricFocus) + "\n" +
            "- JD Skills Already Touched: " + (coveredTopics.isBlank() ? "None yet" : coveredTopics) + "\n" +
            "- Allowed Difficulty Level: " + difficultyInstruction + " — HARD REQUIREMENT, not a suggestion.\n" +
            "\n" +
            "QUESTIONS ALREADY ASKED — DO NOT REPEAT OR PARAPHRASE ANY OF THESE:\n" +
            neverRepeatBlock + "\n" +
            "\n" +
            "CRITICAL EXECUTION RULES:\n" +
            "1. Questions MUST test a specific skill or requirement listed in the JD or rubric — not a project or system the candidate happened to mention.\n" +
            "2. Use the candidate's last answer ONLY to calibrate depth and tone. Never make it the subject of the next question.\n" +
            "3. Maintain a natural, peer-level engineering tone. Do NOT say \"Great answer!\" or \"Thanks for sharing.\"\n" +
            "4. NEVER ask a question that is the same as or closely paraphrases any question in the list above.\n" +
            "5. The question's difficulty MUST match \"" + difficultyInstruction + "\" exactly: for easy difficulty, ask foundational/definitional questions with no multi-step reasoning or advanced internals; for medium, expect applied working knowledge; for hard, expect deep trade-off/internals reasoning. Do not ask a harder or easier question than this level.\n" +
            "6. Output ONLY the raw question string. No markdown wrappers, no explanations." +
            (isProgrammingEnabled(req)
                ? ""
                : "\n7. THEORY-ONLY interview: ask verbal/conceptual questions only. Do NOT ask the candidate to write code or use a code editor.");

        String lastAnswer = req.getLastAnswer() != null ? req.getLastAnswer().trim() : "";
        String recent = buildTranscriptContext(req.getUtterances(), req.getSlot());

        StringBuilder user = new StringBuilder();
        appendDialogueContext(user, req, lastAnswer, recent);
        user.append("Role: ").append(req.getJdTitle()).append("\n");
        if (req.getFocusAreas() != null && !req.getFocusAreas().isBlank()) {
            user.append("Focus: ").append(req.getFocusAreas()).append("\n");
        }
        // Resume on EVERY slot — full on slot 1 (personalised opener), key highlights only after.
        // Without this the model loses candidate context and can't tailor questions to their background.
        if (req.getResumeSummary() != null && !req.getResumeSummary().isBlank()) {
            int resumeChars = req.getSlot() == 1 ? RESUME_DIGEST_CHARS : RESUME_DIGEST_CHARS / 3;
            user.append(req.getSlot() == 1 ? "Candidate resume:\n" : "Candidate resume highlights:\n")
                .append(truncateText(req.getResumeSummary(), resumeChars))
                .append("\n\n");
        }
        // JD on every slot — primary anchor for what skills to test.
        if (req.getJdText() != null && !req.getJdText().isBlank()) {
            user.append("Job Description (skills to test):\n")
                .append(truncateText(req.getJdText(), JD_DIGEST_CHARS))
                .append("\n\n");
        }
        if (thinAnswerMode) {
            user.append("The candidate's last answer was thin — do NOT probe it again. "
                + "Pick a JD skill from the list above that hasn't been covered yet and ask a focused question about it.");
        } else {
            user.append(lastAnswer.isEmpty()
                ? "Ask your opening technical question now. Anchor it in the JD requirements and the candidate's resume background. One or two sentences."
                : "The candidate just answered. Pick the next uncovered JD skill from the list above and ask a focused question about it. "
                + "Do NOT follow up on whatever project they just mentioned — move to a different JD requirement.");
        }

        String result = llmClient.chatQuestionWithSlotAndTracking(system, user.toString(), req.getSlot(), req.getInterviewId(), userId);
        return result.replaceAll("^[\"'\\s]+|[\"'\\s]+$", "");
    }

    /**
     * ONBOARDING question generation — tests foundational understanding of one stated concept,
     * not JD/role fit. Deliberately simpler than llmQuestion: no rubric categories, no seniority
     * calibration from resume, no "role" framing (the concept is not a job).
     */
    private String llmOnboardingQuestion(NextQuestionRequest req, String userId, boolean thinAnswerMode) throws Exception {
        String slotTheme = ONBOARDING_SLOT_THEMES.getOrDefault(
            ((req.getSlot() - 1) % ONBOARDING_SLOT_THEMES.size()) + 1,
            "Applied understanding of the concept.");

        List<String> allPastBotQuestions = extractRecentBotQuestions(req.getUtterances(), ALL_BOT_QUESTIONS_DEDUP);
        String neverRepeatBlock = allPastBotQuestions.isEmpty()
            ? "None yet."
            : allPastBotQuestions.stream()
                .map(q -> "- " + truncateText(q, 120))
                .collect(Collectors.joining("\n"));

        String system =
            "You are a friendly onboarding assessor checking whether a new hire has basic, correct understanding " +
            "of ONE specific concept — this is NOT a job interview and there is no role to assess fit for. " +
            "Your output must be exactly ONE question (1-2 sentences max).\n" +
            "\n" +
            "ASSESSMENT CONTEXT:\n" +
            "- Current focus: " + slotTheme + "\n" +
            "- Allowed difficulty: entry-level / foundational only — do not ask advanced, framework-internal, " +
            "or production-scale questions.\n" +
            "\n" +
            "QUESTIONS ALREADY ASKED — DO NOT REPEAT OR PARAPHRASE ANY OF THESE:\n" +
            neverRepeatBlock + "\n" +
            "\n" +
            "CRITICAL EXECUTION RULES:\n" +
            "1. Stay strictly within the stated concept's scope — do not drift into unrelated or advanced topics.\n" +
            "2. Maintain a warm, encouraging tone — this is a learning check, not a high-pressure interview.\n" +
            "3. NEVER ask a question that is the same as or closely paraphrases any question in the list above.\n" +
            "4. Output ONLY the raw question string. No markdown wrappers, no explanations.";

        String lastAnswer = req.getLastAnswer() != null ? req.getLastAnswer().trim() : "";
        String recent = buildTranscriptContext(req.getUtterances(), req.getSlot());

        StringBuilder user = new StringBuilder();
        appendDialogueContext(user, req, lastAnswer, recent);
        user.append("Concept/Topic: ").append(req.getJdTitle()).append("\n");
        if (req.getJdText() != null && !req.getJdText().isBlank()) {
            user.append("Concept description:\n").append(truncateText(req.getJdText(), JD_DIGEST_CHARS)).append("\n\n");
        }
        if (thinAnswerMode) {
            user.append("The candidate's last answer was thin — do NOT probe it again. "
                + "Move to a NEW angle on the concept that fits the current focus area. "
                + "Ask your next question now.");
        } else {
            user.append(lastAnswer.isEmpty()
                ? "Ask your opening question now, checking basic understanding of the concept. One or two sentences."
                : "Ask your next question now. Must follow from their last answer.");
        }

        String result = llmClient.chatQuestionWithSlotAndTracking(system, user.toString(), req.getSlot(), req.getInterviewId(), userId);
        return result.replaceAll("^[\"'\\s]+|[\"'\\s]+$", "");
    }

    private String extractCoveredTopics(List<NextQuestionRequest.Utterance> utterances) {
        if (utterances == null || utterances.isEmpty()) return "";
        // Collect the last 4 candidate answers, truncated to 80 chars each.
        // A short snippet is enough to tell the model "this skill came up" without dumping
        // full project narratives that cause the model to keep drilling into the same system.
        List<String> recent = new ArrayList<>();
        for (int i = utterances.size() - 1; i >= 0 && recent.size() < 4; i--) {
            NextQuestionRequest.Utterance u = utterances.get(i);
            if ("CANDIDATE".equals(u.speaker()) && u.text() != null && !u.text().isBlank()) {
                recent.add(truncateText(u.text().trim(), 80));
            }
        }
        java.util.Collections.reverse(recent);
        return String.join(" | ", recent);
    }

    private String truncateText(String text, int maxChars) {
        if (text == null || text.isBlank()) return "";
        String trimmed = text.trim();
        if (trimmed.length() <= maxChars) return trimmed;
        return trimmed.substring(0, maxChars) + "...";
    }

    private String formatUtteranceLine(NextQuestionRequest.Utterance utterance) {
        String role = "BOT".equals(utterance.speaker()) ? "Interviewer" : "Candidate";
        return role + ": " + truncateText(utterance.text(), TRANSCRIPT_UTTERANCE_MAX_CHARS);
    }

    private String buildRollingSummary(List<NextQuestionRequest.Utterance> utterances) {
        if (utterances == null || utterances.size() <= TRANSCRIPT_TAIL_UTTERANCES) return "";
        int headEnd = utterances.size() - TRANSCRIPT_TAIL_UTTERANCES;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < headEnd; i++) {
            sb.append(formatUtteranceLine(utterances.get(i))).append("\n");
        }
        return truncateText(sb.toString().trim(), ROLLING_SUMMARY_MAX_CHARS);
    }

    private String buildTranscriptContext(List<NextQuestionRequest.Utterance> utterances, int slot) {
        if (utterances == null || utterances.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        if (slot > ROLLING_SUMMARY_SLOT_THRESHOLD) {
            String summary = buildRollingSummary(utterances);
            if (!summary.isBlank()) {
                sb.append("Earlier conversation (compressed):\n").append(summary).append("\n\n");
            }
        }

        int total = utterances.size();
        int tailSize = Math.min(TRANSCRIPT_TAIL_UTTERANCES, total);
        List<NextQuestionRequest.Utterance> tail = utterances.subList(total - tailSize, total);
        sb.append("Recent exchanges:\n");
        tail.forEach(u -> sb.append(formatUtteranceLine(u)).append("\n"));
        return sb.toString().trim();
    }

    private void appendDialogueContext(StringBuilder user, NextQuestionRequest req, String lastAnswer, String recent) {
        if (!lastAnswer.isEmpty()) {
            user.append("Last answer:\n")
                .append(truncateText(lastAnswer, LAST_ANSWER_MAX_CHARS))
                .append("\n\n");
        } else {
            user.append("(Opening — no prior answer yet)\n\n");
        }
        if (!recent.isEmpty()) {
            user.append(recent).append("\n\n");
        }
    }

    private String formatCompactBankQuestionLine(int index, com.fasterxml.jackson.databind.JsonNode question) {
        return index + ". [ID: " + question.path("id").asText() + "] "
            + "[Type: " + question.path("questionType").asText("TECHNICAL") + "] "
            + "[Relevancy: " + question.path("relevancyLabel").asText("MEDIUM") + "] "
            + "\"" + truncateText(question.path("text").asText(), QUESTION_BANK_SNIPPET_CHARS) + "\"\n";
    }

    private String buildCompactQuestionBankContext(List<com.fasterxml.jackson.databind.JsonNode> unusedQuestions) {
        boolean hasHighOrMedium = unusedQuestions.stream()
            .anyMatch(q -> relevancyScore(q.path("relevancyLabel").asText("MEDIUM")) >= 2);
        StringBuilder questionBankContext = new StringBuilder("Available Question Bank Questions:\n");
        int line = 1;
        for (com.fasterxml.jackson.databind.JsonNode q : unusedQuestions) {
            if (hasHighOrMedium && relevancyScore(q.path("relevancyLabel").asText("MEDIUM")) < 2) {
                continue;
            }
            questionBankContext.append(formatCompactBankQuestionLine(line++, q));
        }
        if (questionBankContext.length() < 80) {
            questionBankContext.setLength(0);
            questionBankContext.append("Available Question Bank Questions:\n");
            line = 1;
            for (com.fasterxml.jackson.databind.JsonNode q : unusedQuestions) {
                questionBankContext.append(formatCompactBankQuestionLine(line++, q));
            }
        }
        return questionBankContext.toString();
    }

    private String resolveBankQuestionText(
            String questionBankId,
            List<com.fasterxml.jackson.databind.JsonNode> unusedQuestions,
            String fallbackText) {
        if (questionBankId == null || questionBankId.isBlank()) {
            return fallbackText;
        }
        for (com.fasterxml.jackson.databind.JsonNode q : unusedQuestions) {
            if (questionBankId.equals(q.path("id").asText())) {
                return q.path("text").asText(fallbackText);
            }
        }
        return fallbackText;
    }

    /** Explicit per-round difficulty override wins; falls back to the mode's default. */
    String resolveDifficulty(NextQuestionRequest req) { // package-private for tests
        String override = req.getQuestionDifficulty();
        if (override != null && !override.isBlank()) {
            switch (override.trim().toUpperCase()) {
                case "EASY": return "easy difficulty";
                case "MEDIUM": return "medium difficulty";
                case "HARD": return "hard difficulty";
                default:
                    log.warn("Unknown questionDifficulty '{}' — falling back to mode default", override);
            }
        }
        return getDifficultyForMode(req.getInterviewMode() != null ? req.getInterviewMode() : "L3");
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
        
        if (isSkipOrAdvanceRequest(answer)) {
            log.info("Detected skip request: '{}' - NOT treating as vague, will progress naturally", trimmed);
            return false;
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

    /** Long STT ramble with little distinct content — treat like vague so we do not loop cross-questions. */
    private boolean isLowSubstanceAnswer(String answer) {
        if (answer == null || answer.isBlank()) return false;
        String trimmed = answer.trim().toLowerCase();
        String[] words = trimmed.split("\\s+");
        if (words.length < 20) return false;

        Set<String> unique = new HashSet<>(Arrays.asList(words));
        double uniqueRatio = (double) unique.size() / words.length;
        if (uniqueRatio < 0.38) {
            log.debug("Low substance: repetitive wording (unique ratio {})", uniqueRatio);
            return true;
        }

        // Very short "sentences" chained without structure (common in noisy STT)
        long singleWordRuns = 0;
        for (String w : words) {
            if (w.length() <= 2) singleWordRuns++;
        }
        if (singleWordRuns > words.length * 0.25 && uniqueRatio < 0.5) {
            log.debug("Low substance: noisy STT fragment ratio");
            return true;
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

        // Cycle through fallback questions for extended timer-based interviews
        String[] extendedFallbacks = {
            "Walk me through one recent project you owned — problem, what you built, and how you knew it worked.",
            "A situation where you had to trade off speed versus correctness — how did you decide?",
            "Explain one technical idea you'd want a peer to understand quickly — where it shines and where it falls apart.",
            "If that system had to run in production tomorrow, how would you roll it out and what would you watch first?",
            "An edge case or incident — what breaks first, and how do you harden it?",
            "Sketch a system that fits what we've been discussing — components, data flow, main risk.",
            "A concrete performance bottleneck in this space — your approach, what you measured, how you fixed it.",
            "Explain a tricky technical trade-off as you would to a sharp but rushed peer.",
            "A vague requirement — how do you turn it into a concrete technical plan with checkpoints?",
            "What would you change about a past design decision, knowing what you know now?",
            "Describe a time you disagreed with a teammate on a technical choice — how did you resolve it?",
            "What's the most complex debugging session you've handled, and how did you trace the root cause?",
        };
        return extendedFallbacks[(slot - 2) % extendedFallbacks.length];
    }

    private List<String> extractRubricLabels(String rubricJson) {
        if (rubricJson == null || rubricJson.isBlank()) return List.of();
        try {
            com.fasterxml.jackson.databind.JsonNode rubric =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(rubricJson);
            com.fasterxml.jackson.databind.JsonNode cats = rubric.path("categories");
            if (!cats.isArray()) return List.of();
            List<String> labels = new ArrayList<>();
            cats.forEach(c -> {
                String label = c.path("label").asText("").trim();
                if (!label.isBlank()) labels.add(label);
            });
            return labels;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String pickTargetSkill(int slot, List<String> rubricLabels, String coveredTopics) {
        if (rubricLabels.isEmpty()) return "";
        String covered = coveredTopics == null ? "" : coveredTopics.toLowerCase();
        List<String> missing = rubricLabels.stream()
            .filter(label -> !covered.contains(label.toLowerCase()))
            .toList();
        List<String> source = missing.isEmpty() ? rubricLabels : missing;
        int idx = Math.max(0, (slot - 1) % source.size());
        return source.get(idx);
    }

    private String buildCoverageHint(List<String> rubricLabels, String coveredTopics) {
        if (rubricLabels.isEmpty()) return "";
        String covered = coveredTopics == null ? "" : coveredTopics.toLowerCase();
        List<String> missing = rubricLabels.stream()
            .filter(label -> !covered.contains(label.toLowerCase()))
            .limit(3)
            .toList();
        if (missing.isEmpty()) return "All rubric areas have at least one touchpoint. Go deeper on weakest evidence.";
        return "Must-cover remaining (prioritize soon): " + String.join(", ", missing);
    }

    private int countRecentProbeQuestions(List<NextQuestionRequest.Utterance> utterances) {
        if (utterances == null || utterances.isEmpty()) return 0;
        List<String> recentBot = extractRecentBotQuestions(utterances, 8);
        int count = 0;
        for (String text : recentBot) {
            String lower = text.toLowerCase();
            if (lower.contains("elaborate")
                || lower.contains("tell me more")
                || lower.contains("specific example")
                || lower.contains("concrete")
                || lower.contains("didn't quite catch")
                || lower.contains("didnt quite catch")
                || lower.contains("can you explain")
                || lower.contains("how does")
                || lower.contains("what is partition")
                || lower.contains("what are topics")) {
                count++;
            }
        }
        count += countSimilarRepeatedBotQuestions(utterances);
        return count;
    }

    private List<String> extractRecentBotQuestions(List<NextQuestionRequest.Utterance> utterances, int max) {
        if (utterances == null || utterances.isEmpty()) return List.of();
        List<String> bot = new ArrayList<>();
        for (int i = utterances.size() - 1; i >= 0 && bot.size() < max; i--) {
            NextQuestionRequest.Utterance u = utterances.get(i);
            if ("BOT".equals(u.speaker()) && u.text() != null && !u.text().isBlank()
                    && !u.text().trim().startsWith(EXPLANATION_PREFIX)) {
                bot.add(u.text().trim());
            }
        }
        return bot;
    }

    private int countSimilarRepeatedBotQuestions(List<NextQuestionRequest.Utterance> utterances) {
        List<String> recent = extractRecentBotQuestions(utterances, 5);
        int repeats = 0;
        for (int i = 0; i < recent.size() - 1; i++) {
            if (isQuestionSimilar(recent.get(i), recent.get(i + 1))) repeats++;
        }
        return repeats;
    }

    private int countRecentCrossQuestions(List<NextQuestionRequest.Utterance> utterances) {
        return countSimilarRepeatedBotQuestions(utterances);
    }

    /**
     * Real cadence gate: cross-questions are allowed at most once every
     * {@link #CROSS_QUESTION_COOLDOWN_SLOTS} question slots. Uses the persisted, ordered
     * question-source history (one entry per slot: AI_GENERATED / QUESTION_BANK /
     * AI_CROSS_QUESTION / ...) when available — this is the ground truth, unlike scanning
     * transcript text for similarity. Falls back to the old similarity-based heuristic only
     * when no source history was supplied (e.g. very first slots, or older clients).
     */
    private boolean shouldBlockCrossQuestion(NextQuestionRequest req) {
        String sourcesRaw = req.getRecentQuestionSources();
        if (sourcesRaw != null && !sourcesRaw.isBlank()) {
            String[] sources = sourcesRaw.split(",");
            int slotsSinceLastCross = 0;
            boolean sawCross = false;
            for (int i = sources.length - 1; i >= 0; i--) {
                if ("AI_CROSS_QUESTION".equalsIgnoreCase(sources[i].trim())) {
                    sawCross = true;
                    break;
                }
                slotsSinceLastCross++;
            }
            if (sawCross && slotsSinceLastCross < CROSS_QUESTION_COOLDOWN_SLOTS) {
                return true;
            }
            // Cadence gate is open — still guard against the model re-asking a near-duplicate
            // of something it just said.
            return countSimilarRepeatedBotQuestions(req.getUtterances()) >= 1;
        }

        // No source history supplied — fall back to the original heuristic.
        return countRecentCrossQuestions(req.getUtterances()) >= MAX_CROSS_QUESTIONS_PER_SLOT
            || countSimilarRepeatedBotQuestions(req.getUtterances()) >= 1;
    }

    private boolean isQuestionSimilarToRecent(String question, List<String> recent) {
        if (question == null || question.isBlank() || recent == null) return false;
        for (String r : recent) {
            if (isQuestionSimilar(question, r)) return true;
        }
        return false;
    }

    private boolean isQuestionSimilar(String a, String b) {
        if (a == null || b == null) return false;
        String na = normalizeQuestion(a);
        String nb = normalizeQuestion(b);
        if (na.isBlank() || nb.isBlank()) return false;
        if (na.equals(nb)) return true;
        if (na.length() >= 25 && nb.length() >= 25 && (na.contains(nb) || nb.contains(na))) return true;
        return wordSimilarity(a, b) >= QUESTION_SIMILARITY_THRESHOLD;
    }

    private static String normalizeQuestion(String q) {
        return q.toLowerCase()
            .replaceAll("[^a-z0-9\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static double wordSimilarity(String a, String b) {
        Set<String> wa = tokenSet(a);
        Set<String> wb = tokenSet(b);
        if (wa.isEmpty() || wb.isEmpty()) return 0;
        long inter = wa.stream().filter(wb::contains).count();
        long union = wa.size() + wb.size() - inter;
        return union == 0 ? 0 : (double) inter / union;
    }

    private static Set<String> tokenSet(String text) {
        Set<String> tokens = new HashSet<>();
        for (String w : normalizeQuestion(text).split("\\s+")) {
            if (w.length() > 2) tokens.add(w);
        }
        return tokens;
    }

    private static int relevancyScore(String label) {
        if (label == null) return 2;
        return switch (label.trim().toUpperCase()) {
            case "HIGH" -> 3;
            case "LOW" -> 1;
            default -> 2;
        };
    }

    private boolean resolveIsCoding(String questionText, String questionType) {
        return detectCodingQuestion(questionText, questionType);
    }

    private boolean isSkipOrAdvanceRequest(String answer) {
        if (answer == null || answer.isBlank()) return false;
        if ("[SKIPPED]".equals(answer.trim())) return true;
        String lower = answer.trim().toLowerCase();
        // Single-word or very short inputs that are clearly not answers
        if (lower.length() < 20 && (lower.equals("ok") || lower.equals("okay") || lower.equals("yes") ||
                lower.equals("sure") || lower.equals("alright") || lower.equals("fine"))) return true;
        String[] patterns = {
            "next question", "skip", "skip this", "move on", "go for", "pass this", "pass on",
            "different question", "another question", "can we skip", "let's move", "lets move",
            "i don't know this", "i dont know this", "not prepared", "taiyari", "next please",
            "can you ask", "ask me something", "ask another"
        };
        for (String pattern : patterns) {
            if (lower.contains(pattern)) return true;
        }
        return false;
    }

    /**
     * Detects when the candidate is asking for clarification or rephrasing of the last question
     * rather than attempting to answer it.
     */
    /**
     * Restate an interview question in plainer language without leaking the answer.
     * Returned text always carries {@link #EXPLANATION_PREFIX} so downstream dedup skips it.
     * Falls back to a slow repeat of the original question when the LLM is unavailable.
     */
    public String explainQuestion(String questionText, String jdTitle, String interviewMode,
                                  String interviewId, String userId) {
        String question = questionText == null ? "" : questionText.trim();
        if (question.isBlank()) {
            return EXPLANATION_PREFIX + "take the question one part at a time and describe your own experience with it.";
        }
        if (llmClient.isConfigured()) {
            try {
                String system =
                    "You are a technical interviewer helping a candidate understand the current question.\n"
                    + "Restate the question below in simpler, plainer language — 2 to 3 short sentences.\n"
                    + "You may say what KIND of answer is expected (e.g., \"describe a real project\" or \"walk through the steps you would take\").\n"
                    + "RULES:\n"
                    + "1. Do NOT answer the question, hint at the answer, or give examples that reveal the solution.\n"
                    + "2. Do NOT introduce a new or different question.\n"
                    + "3. Keep every technical requirement of the original question intact — simplify the wording, not the task.\n"
                    + "4. Output ONLY the plain explanation text. No preamble, no markdown, no quotes.";
                String user = "Role: " + (jdTitle != null ? jdTitle : "Target role") + "\n"
                    + "Interview question:\n" + truncateText(question, 600) + "\n\n"
                    + "Explain this question in easy terms now.";
                String explanation = llmClient
                    .chatQuestionWithSlotAndTracking(system, user, 0, interviewId, userId)
                    .replaceAll("^[\"'\\s]+|[\"'\\s]+$", "");
                if (!explanation.isBlank()) {
                    return EXPLANATION_PREFIX + explanation;
                }
            } catch (Exception e) {
                log.warn("Question explanation LLM call failed, using plain repeat: {}", e.getMessage());
            }
        }
        return EXPLANATION_PREFIX + "let me repeat it slowly — "
            + Character.toLowerCase(question.charAt(0)) + question.substring(1)
            + " Take your time and answer whichever part you're most comfortable with first.";
    }

    private boolean isClarificationRequest(String answer) {
        if (answer == null || answer.isBlank()) return false;
        String lower = answer.trim().toLowerCase();
        String[] patterns = {
            "can you explain", "what do you mean", "i don't understand", "i dont understand",
            "can't understand", "cannot understand", "what are you asking", "what is the question",
            "please clarify", "clarify the question", "i'm confused", "im confused",
            "what exactly", "rephrase", "repeat the question", "say that again",
            "i'm not sure what you", "im not sure what you", "what question"
        };
        for (String pattern : patterns) {
            if (lower.contains(pattern)) return true;
        }
        return false;
    }

    /**
     * Returns a genuinely simplified version of the last bot question (LLM-backed, plain-repeat
     * fallback), or a fresh question if no recent bot question is found. Skips explanation
     * utterances so a second clarification still targets the real question.
     */
    private String rephraseLastQuestion(NextQuestionRequest req, String userId) {
        List<NextQuestionRequest.Utterance> utterances = req.getUtterances();
        if (utterances != null) {
            for (int i = utterances.size() - 1; i >= 0; i--) {
                NextQuestionRequest.Utterance u = utterances.get(i);
                if ("BOT".equals(u.speaker()) && u.text() != null && !u.text().isBlank()
                        && !u.text().trim().startsWith(EXPLANATION_PREFIX)) {
                    String lastQ = u.text().trim();
                    // Strip any meta prefixes like "For [Role], " that snuck through
                    lastQ = lastQ.replaceAll("^For [^,]+, ", "");
                    return explainQuestion(lastQ, req.getJdTitle(), req.getInterviewMode(),
                        req.getInterviewId(), userId);
                }
            }
        }
        return progressQuestionAvoidingRecent(req, req.getSlot());
    }

    /** Pick a fresh question for this slot that does not repeat recent bot dialogue. */
    private String progressQuestionAvoidingRecent(NextQuestionRequest req, int startSlot) {
        List<String> recentBot = extractRecentBotQuestions(req.getUtterances(), 6);
        return pickFreshQuestion(req, startSlot, recentBot);
    }

    /**
     * Like progressQuestionAvoidingRecent but checks against ALL past bot questions, not just the last 6.
     * Used as the definitive dedup fallback to prevent any question from repeating across the full session.
     */
    private String progressQuestionAvoidingAll(NextQuestionRequest req) {
        List<String> allBot = extractRecentBotQuestions(req.getUtterances(), ALL_BOT_QUESTIONS_DEDUP);
        return pickFreshQuestion(req, req.getSlot(), allBot);
    }

    /** Core question-picking logic shared by both progression methods. */
    private String pickFreshQuestion(NextQuestionRequest req, int startSlot, List<String> usedQuestions) {
        if (req.isOnboarding()) {
            return pickFreshOnboardingQuestion(req, usedQuestions);
        }
        String mode = req.getInterviewMode() != null ? req.getInterviewMode() : "L3";
        Map<String, Map<Integer, String>> familyPool = curatedPoolForRole(req.getJdTitle(), req.getJdText());
        Map<Integer, String> curatedQuestions = familyPool.getOrDefault(mode, familyPool.get("L3"));
        int maxDefined = curatedQuestions != null ? curatedQuestions.size() : 5;

        // Try every curated slot before cycling — prevents cycling back to already-asked ones
        if (curatedQuestions != null) {
            for (int effectiveSlot = 1; effectiveSlot <= maxDefined; effectiveSlot++) {
                String curated = curatedQuestions.get(effectiveSlot);
                if (curated != null && !curated.isBlank() && !isQuestionSimilarToRecent(curated, usedQuestions)) {
                    return curated;
                }
            }
        }

        // All curated exhausted — try slot-based fallbacks with increasing offset
        for (int offset = 0; offset < 12; offset++) {
            int slot = startSlot + offset;
            String fallback = slotFallbackQuestion(slot, req.getJdTitle(), offset);
            if (!isQuestionSimilarToRecent(fallback, usedQuestions)) {
                return fallback;
            }
        }

        // Last resort: open-ended alternates unlikely to repeat
        for (String alt : LAST_RESORT_ALTERNATES) {
            if (!isQuestionSimilarToRecent(alt, usedQuestions)) return alt;
        }

        // Even the expanded pool is exhausted (very long, extended-timer interview). Rather than
        // silently repeating a question, synthesize a fresh, distinguishable variant by combining
        // a rotating frame with a rotating engineering angle — the combinatorics (frames × angles)
        // make an exact repeat effectively unreachable, unlike the flat modulo this replaced.
        int depth = usedQuestions.size();
        String frame = LAST_RESORT_FRAMES.get(depth % LAST_RESORT_FRAMES.size());
        String angle = LAST_RESORT_ANGLES.get((depth / LAST_RESORT_FRAMES.size()) % LAST_RESORT_ANGLES.size());
        String synthesized = String.format(frame, angle);
        if (!isQuestionSimilarToRecent(synthesized, usedQuestions)) return synthesized;

        // Truly exhausted (frames × angles also collided) — last-ditch unique tail using depth itself.
        return synthesized + " (follow-up " + depth + ")";
    }

    /** Fallback question picker for ONBOARDING assessments — concept-scoped, not role/mode-scoped. */
    String pickFreshOnboardingQuestion(NextQuestionRequest req, List<String> usedQuestions) { // package-private for tests
        String concept = req.getJdTitle() != null && !req.getJdTitle().isBlank() ? req.getJdTitle() : "this topic";
        for (String template : ONBOARDING_FALLBACK_QUESTIONS) {
            String candidate = String.format(template, concept);
            if (!isQuestionSimilarToRecent(candidate, usedQuestions)) return candidate;
        }
        // All templates exhausted (long extended-timer interview) — cycle with a depth marker.
        String template = ONBOARDING_FALLBACK_QUESTIONS.get(usedQuestions.size() % ONBOARDING_FALLBACK_QUESTIONS.size());
        return "Going deeper — " + String.format(template, concept).substring(0, 1).toLowerCase()
            + String.format(template, concept).substring(1);
    }

    /**
     * Pick the curated question pool matching the JD's role family.
     * Title keywords win (high precision); otherwise the JD text is scored per family and the
     * winner must have at least 2 keyword hits — anything ambiguous falls back to the
     * role-neutral pool so we never ask backend-dev questions in a non-backend interview.
     */
    Map<String, Map<Integer, String>> curatedPoolForRole(String jdTitle, String jdText) { // package-private for tests
        String family = inferRoleFamily(jdTitle, jdText);
        return switch (family) {
            case "BACKEND" -> MODE_SLOT_QUESTIONS;
            case "DEVOPS" -> DEVOPS_MODE_SLOT_QUESTIONS;
            default -> GENERIC_MODE_SLOT_QUESTIONS;
        };
    }

    private static final Map<String, String[]> ROLE_TITLE_SIGNALS = Map.of(
        "DEVOPS", new String[]{"devops", "site reliability", "sre", "platform engineer", "infrastructure engineer",
            "cloud engineer", "release engineer", "build and release", "systems engineer"},
        "BACKEND", new String[]{"backend", "back-end", "back end", "java developer", "java engineer", "spring",
            "node", "python developer", "python engineer", ".net", "golang", "go developer", "api developer",
            "full stack", "full-stack", "fullstack"},
        "FRONTEND", new String[]{"frontend", "front-end", "front end", "react", "angular", "vue", "ui developer",
            "ui engineer", "web developer"},
        "DATA", new String[]{"data engineer", "data scientist", "machine learning", "ml engineer", "ai engineer",
            "data analyst", "big data", "etl"},
        "MOBILE", new String[]{"android", "ios", "mobile", "flutter", "react native"},
        "QA", new String[]{"qa", "test engineer", "sdet", "quality assurance", "automation tester", "test analyst"}
    );

    private static final Map<String, String[]> ROLE_TEXT_SIGNALS = Map.of(
        "DEVOPS", new String[]{"devops", "terraform", "ansible", "kubernetes", "docker", "jenkins", "ci/cd",
            "prometheus", "grafana", "helm", "cloudformation", "infrastructure as code", "site reliability"},
        "BACKEND", new String[]{"spring boot", "spring", "hibernate", "java", "node.js", "django", "microservices",
            "rest api", "restful", "kafka", ".net", "golang", "backend"},
        "FRONTEND", new String[]{"react", "angular", "vue", "css", "html", "frontend", "responsive", "ui/ux",
            "javascript", "typescript"},
        "DATA", new String[]{"machine learning", "data pipeline", "spark", "hadoop", "etl", "data warehouse",
            "pandas", "tensorflow", "pytorch", "airflow"},
        "MOBILE", new String[]{"android", "ios", "swift", "kotlin", "flutter", "react native", "mobile app"},
        "QA", new String[]{"selenium", "test automation", "test cases", "sdet", "cypress", "playwright",
            "quality assurance"}
    );

    /** Deterministic match order: specific families first, BACKEND last (its keywords are broadest). */
    private static final List<String> ROLE_FAMILY_ORDER =
        List.of("DEVOPS", "FRONTEND", "DATA", "MOBILE", "QA", "BACKEND");

    String inferRoleFamily(String jdTitle, String jdText) { // package-private for tests
        String title = jdTitle == null ? "" : jdTitle.toLowerCase();
        for (String family : ROLE_FAMILY_ORDER) {
            for (String signal : ROLE_TITLE_SIGNALS.get(family)) {
                if (title.contains(signal)) return family;
            }
        }
        String text = jdText == null ? "" : truncateText(jdText, 3000).toLowerCase();
        if (text.isBlank()) return "GENERIC";
        String bestFamily = "GENERIC";
        int bestScore = 0;
        boolean tie = false;
        for (String family : ROLE_FAMILY_ORDER) {
            int score = 0;
            for (String signal : ROLE_TEXT_SIGNALS.get(family)) {
                if (text.contains(signal)) score++;
            }
            if (score > bestScore) {
                bestScore = score;
                bestFamily = family;
                tie = false;
            } else if (score == bestScore && score > 0) {
                tie = true;
            }
        }
        // Require a clear winner with at least 2 signals; otherwise stay neutral
        if (bestScore < 2 || tie) return "GENERIC";
        return bestFamily;
    }

    private String slotFallbackQuestion(int slot, String jdTitle, int variantSeed) {
        String role = jdTitle != null && !jdTitle.isBlank() ? jdTitle : "this role";
        List<String> variants = switch (slot) {
            case 2 -> List.of(
                "Walk me through one recent project you owned for " + role + " — problem, what you built, and how you knew it worked.",
                "What was the hardest technical call in a recent project, and what would you do differently?");
            case 3 -> List.of(
                "A situation where you traded speed versus correctness — how did you decide?",
                "What broke or got messy in practice on a recent project, and how did you notice?");
            case 4 -> List.of(
                "Explain a technical idea you'd want a peer to understand quickly — where it shines and where it falls apart.",
                "If that idea had to run in production tomorrow, how would you roll it out and what would you watch first?");
            case 5 -> List.of(
                "An edge case or incident — what breaks first, and how do you harden against it?",
                "Sketch a system that fits what we've been discussing — components, data flow, and main risk.");
            case 6 -> List.of(
                "What monitoring or metrics would you add first for a system like the one you described?",
                "What's the worst production failure you've seen in this space, and what changed afterward?");
            case 7 -> List.of(
                "A concrete problem in this space — your approach, complexity, and how you'd test it.",
                "Explain a tricky technical trade-off as you would to a sharp but rushed peer.");
            default -> List.of(
                "What's a concept you'd defend in design review for " + role + ", and what's the main pushback you'd expect?",
                "A vague requirement landed on your desk — how do you turn it into a concrete technical plan with checkpoints?");
        };
        return variants.get(Math.floorMod(variantSeed, variants.size()));
    }

    private QuestionResult selectFromQuestionBank(NextQuestionRequest req, String userId) throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode questionsArray = mapper.readTree(req.getQuestionBankQuestionsJson());
        
        if (!questionsArray.isArray() || questionsArray.size() == 0) {
            return null;
        }

        // Parse used question IDs
        Set<String> usedIds = new java.util.HashSet<>();
        if (req.getUsedQuestionIds() != null && !req.getUsedQuestionIds().isBlank()) {
            usedIds.addAll(java.util.Arrays.asList(req.getUsedQuestionIds().split(",")));
        }

        // Filter unused questions
        List<com.fasterxml.jackson.databind.JsonNode> unusedQuestions = new ArrayList<>();
        for (com.fasterxml.jackson.databind.JsonNode q : questionsArray) {
            String qId = q.path("id").asText();
            if (!usedIds.contains(qId)) {
                unusedQuestions.add(q);
            }
        }

        if (unusedQuestions.isEmpty()) {
            log.info("All question bank questions have been used, falling back to AI generation");
            return null;
        }

        unusedQuestions = filterTheoryOnlyBankQuestions(req, unusedQuestions);
        if (unusedQuestions.isEmpty()) {
            log.info("No non-coding question bank questions remain for theory-only interview, falling back to AI generation");
            return null;
        }

        if (shouldBlockCrossQuestion(req)) {
            log.info("Cross-question cap reached — selecting next bank question deterministically");
            return pickBestBankQuestionFallback(req, unusedQuestions, "CROSS_CAP");
        }

        // Let AI select the best question from question bank
        String selectedQuestionJson = llmSelectQuestionFromBank(req, unusedQuestions, userId);
        com.fasterxml.jackson.databind.JsonNode selectedQuestion = mapper.readTree(selectedQuestionJson);

        String questionText = selectedQuestion.path("question").asText();
        String questionBankId = selectedQuestion.path("questionBankId").asText("");
        if (questionBankId.isBlank()) questionBankId = null;
        String source = selectedQuestion.path("source").asText("QUESTION_BANK");
        String preferredLanguage = selectedQuestion.path("preferredLanguage").asText("");
        if (preferredLanguage.isBlank()) preferredLanguage = null;

        List<String> recentBot = extractRecentBotQuestions(req.getUtterances(), RECENT_BOT_QUESTIONS_FOR_DEDUP);
        boolean crossQuestion = "AI_CROSS_QUESTION".equalsIgnoreCase(source);
        if (crossQuestion && (shouldBlockCrossQuestion(req) || isQuestionSimilarToRecent(questionText, recentBot))) {
            log.info("Blocked duplicate/overflow cross-question — using bank fallback");
            return pickBestBankQuestionFallback(req, unusedQuestions, "CROSS_DEDUP");
        }

        String bankQuestionType = null;
        if (questionBankId != null && !questionBankId.isBlank() && !crossQuestion) {
            questionText = resolveBankQuestionText(questionBankId, unusedQuestions, questionText);
            for (com.fasterxml.jackson.databind.JsonNode q : unusedQuestions) {
                if (questionBankId.equals(q.path("id").asText())) {
                    bankQuestionType = q.path("questionType").asText(null);
                    break;
                }
            }
        }

        if (isQuestionSimilarToRecent(questionText, recentBot)) {
            log.info("Bank/LLM question duplicates recent dialogue — using bank fallback");
            return pickBestBankQuestionFallback(req, unusedQuestions, "DEDUP");
        }

        boolean isCoding = resolveIsCoding(questionText, bankQuestionType);
        if (preferredLanguage == null || preferredLanguage.isBlank()) {
            preferredLanguage = inferLanguageFromContext(questionText, req.getJdTitle());
        }
        if (isCoding) {
            preferredLanguage = inferLanguageFromContext(questionText, req.getJdTitle());
        }

        log.info("Selected question from question bank: ID={}, source={}, isCoding={}", questionBankId, source, isCoding);
        return new QuestionResult(questionText, false, false,
            questionBankId != null && !questionBankId.isBlank() ? questionBankId : null,
            source,
            isCoding, preferredLanguage, null);
    }

    private QuestionResult pickBestBankQuestionFallback(
            NextQuestionRequest req,
            List<com.fasterxml.jackson.databind.JsonNode> unusedQuestions,
            String reason) {
        Integer codingSlot = codingSlotForMode(req.getInterviewMode() != null ? req.getInterviewMode() : "L3");
        boolean wantCoding = isProgrammingEnabled(req) && codingSlot != null && req.getSlot() == codingSlot
            && countCodingQuestionsAsked(req.getUtterances()) == 0;

        com.fasterxml.jackson.databind.JsonNode best = null;
        int bestScore = -1;
        for (com.fasterxml.jackson.databind.JsonNode q : unusedQuestions) {
            String type = q.path("questionType").asText("TECHNICAL");
            boolean isBankCoding = "CODING".equalsIgnoreCase(type);
            if (wantCoding && !isBankCoding) continue;
            if (!wantCoding && isBankCoding) continue;

            int score = relevancyScore(q.path("relevancyLabel").asText("MEDIUM"));
            boolean hasHigher = unusedQuestions.stream()
                .anyMatch(other -> relevancyScore(other.path("relevancyLabel").asText("MEDIUM")) > 1);
            if (hasHigher && score < 2) continue;

            if (score > bestScore) {
                bestScore = score;
                best = q;
            }
        }
        if (best == null) {
            for (com.fasterxml.jackson.databind.JsonNode q : unusedQuestions) {
                int score = relevancyScore(q.path("relevancyLabel").asText("MEDIUM"));
                if (score > bestScore) {
                    bestScore = score;
                    best = q;
                }
            }
        }
        if (best == null) return null;

        String questionText = best.path("text").asText();
        String bankQuestionType = best.path("questionType").asText(null);
        String questionBankId = best.path("id").asText();
        boolean isCoding = resolveIsCoding(questionText, bankQuestionType);
        String lang = inferLanguageFromContext(questionText, req.getJdTitle());
        log.info("Bank fallback ({}) ID={}, relevancy={}", reason, questionBankId, best.path("relevancyLabel").asText(""));
        return new QuestionResult(questionText, false, false, questionBankId, "QUESTION_BANK", isCoding, lang, null);
    }

    private String llmSelectQuestionFromBank(NextQuestionRequest req, List<com.fasterxml.jackson.databind.JsonNode> unusedQuestions, String userId) throws Exception {
        String mode = req.getInterviewMode() != null ? req.getInterviewMode() : "L3";
        Map<Integer, String> slotThemes = MODE_SLOT_THEMES.getOrDefault(mode, MODE_SLOT_THEMES.get("L3"));
        String slotTheme = slotThemes.getOrDefault(req.getSlot(),
            "Continue probing technical depth and communication quality relevant to the role.");

        String questionBankContext = buildCompactQuestionBankContext(unusedQuestions);

        List<String> recentBot = extractRecentBotQuestions(req.getUtterances(), RECENT_BOT_QUESTIONS_FOR_DEDUP);
        String recentQuestionsBlock = recentBot.isEmpty()
            ? "None"
            : recentBot.stream()
                .map(q -> truncateText(q, 120))
                .collect(Collectors.joining("\n- ", "- ", ""));

        boolean crossBlocked = shouldBlockCrossQuestion(req);

        String system =
            "You are an expert technical recruiter. Select EXACTLY ONE question from the available question bank context.\n" +
            "React naturally to the candidate's previous answer and reference specific engineering details when available.\n" +
            "\n" +
            "AVAILABLE DATA WINDOW:\n" +
            "Question Bank: " + questionBankContext + "\n" +
            "Active Slot Theme: " + slotTheme + "\n" +
            "Active Mode: " + mode + "\n" +
            "Target Difficulty: " + resolveDifficulty(req) + " — you MUST ONLY select bank questions whose complexity matches this level; if none of the unused bank questions fit, do not force a mismatched one — pick the closest available and note it is a fallback. Cross-questions must also match this level.\n" +
            "Recently asked (DO NOT repeat or paraphrase closely):\n" + recentQuestionsBlock + "\n" +
            "\n" +
            "SELECTION LOGIC:\n" +
            "1. Prefer unused bank questions with relevancy HIGH, then MEDIUM. Do not pick LOW if HIGH/MEDIUM exist.\n" +
            "2. Cross-question (source AI_CROSS_QUESTION) only if the last answer needs one brief clarification. Cross-questions are RATIONED — at most one every " + CROSS_QUESTION_COOLDOWN_SLOTS + " question slots, never back-to-back or on consecutive topics.\n" +
            (crossBlocked ? "3. Cross-questions are DISALLOWED this turn — you MUST return source QUESTION_BANK with a new unused question ID.\n"
                : "3. If you cross-question, it must be a NEW angle — never repeat the previous interviewer question.\n") +
            "4. isCoding MUST be true ONLY when the candidate must write executable code in an editor (implement function, algorithm with I/O). Conceptual, architecture, and Kafka/system-design questions MUST have isCoding false.\n" +
            (isProgrammingEnabled(req)
                ? "5. preferredLanguage only when isCoding is true; otherwise null.\n"
                : "5. THEORY-ONLY interview: isCoding MUST be false. Do NOT select CODING-type bank questions. Cross-questions must stay verbal/conceptual.\n") +
            "\n" +
            "OUTPUT SPECIFICATION:\n" +
            "You must output ONLY a valid, raw JSON object. Do NOT wrap it in markdown fences like ```json. Do NOT include any trailing comments.\n" +
            "{\n" +
            "  \"question\": \"Cross-question text only when source is AI_CROSS_QUESTION; otherwise empty string\",\n" +
            "  \"questionBankId\": \"Insert UUID string or null if AI_CROSS_QUESTION\",\n" +
            "  \"source\": \"QUESTION_BANK\" or \"AI_CROSS_QUESTION\",\n" +
            "  \"isCoding\": true or false,\n" +
            "  \"preferredLanguage\": \"python|java|javascript|cpp|go|etc or null\"\n" +
            "}";

        String lastAnswer = req.getLastAnswer() != null ? req.getLastAnswer().trim() : "";
        String recent = buildTranscriptContext(req.getUtterances(), req.getSlot());

        StringBuilder user = new StringBuilder();
        appendDialogueContext(user, req, lastAnswer, recent);
        user.append("Select the best question from the question bank or decide to ask a cross-question.\n");
        user.append("For QUESTION_BANK selections, return questionBankId only — the server resolves full question text.\n");
        user.append("Return ONLY valid JSON, no markdown fences.");

        String result = llmClient.chatQuestionWithSlotAndTracking(system, user.toString(), req.getSlot(), req.getInterviewId(), userId);
        
        // Strip markdown fences if present
        result = JsonRepairUtil.repair(result);
        
        return result;
    }

    private QuestionResult selectFromCustomQuestions(NextQuestionRequest req) throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode customQuestionsArray = mapper.readTree(req.getCustomQuestionsJson());
        
        if (!customQuestionsArray.isArray() || customQuestionsArray.size() == 0) {
            return null;
        }

        // Parse used question indices (for custom questions, we track by index since they don't have IDs)
        Set<Integer> usedIndices = new java.util.HashSet<>();
        if (req.getUsedQuestionIds() != null && !req.getUsedQuestionIds().isBlank()) {
            String[] parts = req.getUsedQuestionIds().split(",");
            for (String part : parts) {
                if (part.trim().startsWith("custom_")) {
                    try {
                        int idx = Integer.parseInt(part.trim().substring(7));
                        usedIndices.add(idx);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        // Find next unused custom question (in order)
        for (int i = 0; i < customQuestionsArray.size(); i++) {
            if (!usedIndices.contains(i)) {
                com.fasterxml.jackson.databind.JsonNode qNode = customQuestionsArray.get(i);
                String questionText = qNode.isTextual() ? qNode.asText() : qNode.path("text").asText("");
                String questionType = qNode.isObject() ? qNode.path("questionType").asText("") : "";
                if (questionType.isBlank()) questionType = null;
                String customQuestionId = "custom_" + i;
                boolean isCoding = detectCodingQuestion(questionText, questionType);
                String lang = inferLanguageFromContext(questionText, req.getJdTitle());
                log.info("Selected custom question at index {}: {}", i, questionText.substring(0, Math.min(50, questionText.length())));
                return new QuestionResult(questionText, false, false, customQuestionId, "CUSTOM_QUESTION", isCoding, lang, null);
            }
        }

        log.info("All {} custom questions have been used, falling back to question bank or AI", customQuestionsArray.size());
        return null;
    }

    private Integer codingSlotForMode(String mode) {
        return CODING_SLOT_BY_MODE.get(mode != null ? mode : "L3");
    }

    private boolean isProgrammingEnabled(NextQuestionRequest req) {
        return req.getIncludeProgrammingQuestions() == null
            || Boolean.TRUE.equals(req.getIncludeProgrammingQuestions());
    }

    private boolean isBankCodingQuestion(com.fasterxml.jackson.databind.JsonNode q) {
        return "CODING".equalsIgnoreCase(q.path("questionType").asText("TECHNICAL"));
    }

    private List<com.fasterxml.jackson.databind.JsonNode> filterTheoryOnlyBankQuestions(
            NextQuestionRequest req,
            List<com.fasterxml.jackson.databind.JsonNode> questions) {
        if (isProgrammingEnabled(req)) {
            return questions;
        }
        return questions.stream().filter(q -> !isBankCodingQuestion(q)).toList();
    }


    private String defaultCodingPrompt(NextQuestionRequest req) {
        String lang = inferLanguageFromContext("", req.getJdTitle());
        if ("java".equals(lang)) {
            return "Implement a Java solution that finds the second smallest distinct value in an integer array "
                + "(prefer using streams). Open the code editor, write your solution, and click Run & Submit when ready.";
        }
        if ("javascript".equals(lang) || "typescript".equals(lang)) {
            return "Implement a function that processes an array of numbers and returns the second smallest distinct value. "
                + "Use the code editor and click Run & Submit when your solution is ready.";
        }
        return "Write a short coding solution for a practical algorithm problem (array or string). "
            + "Use the code editor and click Run & Submit when your solution is ready.";
    }

    private QuestionResult applyCodingSlotPolicy(NextQuestionRequest req, QuestionResult result) {
        if (result == null) return null;
        // An explanation is not a question — never convert it into the forced coding prompt
        if (result.question() != null && result.question().startsWith(EXPLANATION_PREFIX)) return result;
        if (!isProgrammingEnabled(req)) {
            if (result.isCoding()) {
                log.info("Theory-only interview — converting coding question to verbal at slot {}", req.getSlot());
                return copyResult(result, result.question(), false);
            }
            return result;
        }
        // Onboarding never forces a fixed coding-editor slot (defaultCodingPrompt is JD-role-flavored,
        // and a short concept check has no natural "coding slot" position). A naturally-arising
        // coding-flavored question is left as-is if the model proposes one.
        if (req.isOnboarding()) {
            return result;
        }
        String mode = req.getInterviewMode() != null ? req.getInterviewMode() : "L3";
        Integer codingSlot = codingSlotForMode(mode);
        if (codingSlot == null) return result;

        int codingCount = countCodingQuestionsAsked(req.getUtterances());
        int slot = req.getSlot();

        // Cap: never more than 2 coding questions in a single interview.
        if (codingCount >= MAX_CODING_QUESTIONS_PER_INTERVIEW && result.isCoding()) {
            log.info("Coding cap ({}) reached — converting slot {} question to verbal", MAX_CODING_QUESTIONS_PER_INTERVIEW, slot);
            return copyResult(result, result.question(), false);
        }

        // Primary coding slot — always forced once, same as before.
        if (slot == codingSlot && codingCount == 0) {
            String q = result.question();
            String bankType = bankQuestionTypeForResult(req, result);
            boolean textIsCoding = detectCodingQuestion(q, bankType);
            if (!textIsCoding) {
                q = defaultCodingPrompt(req);
            } else if (!q.toLowerCase().contains("code editor")) {
                q = q + " Use the code editor and click Run & Submit when your solution is ready.";
            }
            String lang = inferLanguageFromContext(q, req.getJdTitle());
            log.info("Forcing coding question at slot {} for mode {} (textIsCoding={})", slot, mode, textIsCoding);
            return copyResult(result, q, true, lang, result.starterCode());
        }

        // Second coding slot — ONLY if the first submission was wrong/partial. A candidate who got
        // the first program right never sees a second one; the interview stays at 1 coding question.
        int secondCodingSlot = codingSlot + SECOND_CODING_SLOT_OFFSET;
        boolean firstAttemptFailed = "incorrect".equalsIgnoreCase(req.getLastCodeCorrectness())
            || "partial".equalsIgnoreCase(req.getLastCodeCorrectness());
        if (slot == secondCodingSlot && codingCount == 1 && firstAttemptFailed) {
            String q = defaultEasyCodingPrompt(req);
            String lang = inferLanguageFromContext(q, req.getJdTitle());
            log.info("First coding attempt was {} — forcing an EASY second coding question at slot {}",
                req.getLastCodeCorrectness(), slot);
            return copyResult(result, q, true, lang, null);
        }

        // Any other coding-flavored question that isn't one of the two sanctioned slots — verbal only.
        if (result.isCoding()) {
            log.info("Coding detected off-slot (slot {} vs coding slot {}/{}) — verbal only", slot, codingSlot, secondCodingSlot);
            return copyResult(result, result.question(), false);
        }

        return result;
    }

    /** Counts BOT questions in the transcript that look like coding prompts (0, 1, or 2+). */
    private int countCodingQuestionsAsked(List<NextQuestionRequest.Utterance> utterances) {
        if (utterances == null) return 0;
        int count = 0;
        for (NextQuestionRequest.Utterance u : utterances) {
            if ("BOT".equals(u.speaker()) && detectCodingQuestion(u.text(), null)) {
                count++;
            }
        }
        return count;
    }

    private String defaultEasyCodingPrompt(NextQuestionRequest req) {
        String lang = inferLanguageFromContext("", req.getJdTitle());
        if ("java".equals(lang)) {
            return "Let's try a simpler one. Implement a Java method that reverses a string. "
                + "Open the code editor, write your solution, and click Run & Submit when ready.";
        }
        if ("javascript".equals(lang) || "typescript".equals(lang)) {
            return "Let's try a simpler one. Write a function that reverses a string. "
                + "Use the code editor and click Run & Submit when your solution is ready.";
        }
        return "Let's try a simpler one. Write a short function that reverses a string or counts vowels in it. "
            + "Use the code editor and click Run & Submit when your solution is ready.";
    }

    /**
     * Guarantees a client screening checklist's knockout questions actually get asked instead of
     * being left to the model's discretion — each unasked knockout question gets a deadline slot;
     * once reached, it's forced verbatim as this slot's question (unless this slot is already the
     * forced coding slot, which takes priority).
     */
    private QuestionResult applyKnockoutQuestionPolicy(NextQuestionRequest req, QuestionResult result) {
        if (result == null) return null;
        if (result.question() != null && result.question().startsWith(EXPLANATION_PREFIX)) return result;
        if (req.isOnboarding()) return result;

        List<String> knockoutQuestions = parseKnockoutQuestions(req.getRubricJson());
        if (knockoutQuestions.isEmpty()) return result;

        // Coding slot already forced this turn — don't clobber it. The knockout question's deadline
        // simply carries over to the next call, same as it would if the model overran naturally.
        if (result.isCoding()) return result;

        List<String> allBot = extractRecentBotQuestions(req.getUtterances(), ALL_BOT_QUESTIONS_DEDUP);
        int slot = req.getSlot();

        for (int i = 0; i < knockoutQuestions.size(); i++) {
            String knockout = knockoutQuestions.get(i);
            if (isQuestionSimilarToRecent(knockout, allBot)) continue; // already asked
            int deadline = KNOCKOUT_FIRST_DEADLINE_SLOT + i * KNOCKOUT_SLOT_SPACING;
            if (slot >= deadline) {
                log.info("Knockout question {} deadline reached (slot {} >= {}) — forcing it verbatim", i + 1, slot, deadline);
                return copyResult(result, knockout, false);
            }
        }
        return result;
    }

    /** Extracts the checklist's mandatory knockout questions from a rubric (Phase 2 schema). */
    private List<String> parseKnockoutQuestions(String rubricJson) {
        if (rubricJson == null || rubricJson.isBlank()) return List.of();
        try {
            com.fasterxml.jackson.databind.JsonNode rubric =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(rubricJson);
            com.fasterxml.jackson.databind.JsonNode questions =
                rubric.path("screeningChecklist").path("knockoutQuestions");
            List<String> result = new ArrayList<>();
            if (questions.isArray()) {
                questions.forEach(q -> {
                    String text = q.asText("");
                    if (!text.isBlank()) result.add(text);
                });
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    private QuestionResult copyResult(QuestionResult src, String question, boolean isCoding) {
        return copyResult(src, question, isCoding, src.preferredLanguage(), src.starterCode());
    }

    private QuestionResult copyResult(QuestionResult src, String question, boolean isCoding,
                                      String preferredLanguage, String starterCode) {
        return new QuestionResult(
            question,
            src.manipulationDetected(),
            src.terminateInterview(),
            src.questionBankId(),
            src.source(),
            isCoding,
            preferredLanguage != null ? preferredLanguage : "python",
            starterCode
        );
    }

    static boolean detectCodingQuestion(String questionText, String questionType) {
        if (questionType != null && "CODING".equalsIgnoreCase(questionType.trim())) {
            return true;
        }
        if (questionText == null || questionText.isBlank()) return false;
        String lower = questionText.toLowerCase();
        String[] keywords = {
            "write a function", "write code", "write a program", "implement a function", "implement the",
            "coding question", "algorithm", "leetcode", "hackerrank", "time complexity", "space complexity",
            "given an array", "given a string", "input:", "output:", "sample input", "sample output",
            "solve the following", "program to", "function that", "return an array", "return the"
        };
        for (String kw : keywords) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    static String inferLanguageFromContext(String questionText, String jdTitle) {
        String combined = ((questionText != null ? questionText : "") + " " + (jdTitle != null ? jdTitle : "")).toLowerCase();
        if (combined.contains("javascript") || combined.contains("react") || combined.contains("node.js") || combined.contains("typescript")) {
            return combined.contains("typescript") ? "typescript" : "javascript";
        }
        if (combined.contains("java") && !combined.contains("javascript")) return "java";
        if (combined.contains("python")) return "python";
        if (combined.contains("golang") || combined.contains(" go ")) return "go";
        if (combined.contains("c++") || combined.contains("cpp")) return "cpp";
        if (combined.contains("kotlin")) return "kotlin";
        if (combined.contains("c#") || combined.contains("csharp") || combined.contains(".net")) return "csharp";
        if (combined.contains("rust")) return "rust";
        return "python";
    }
}

