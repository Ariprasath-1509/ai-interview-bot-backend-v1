package com.qb.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qb.ai.dto.*;
import com.qb.ai.dto.DigestCommitRequest.CommitQuestion;
import com.qb.ai.dto.DigestCommitRequest.CommitSession;
import com.qb.ai.dto.DigestParseResponse.*;
import com.qb.ai.llm.PromptTemplates;
import com.qb.core.entity.*;
import com.qb.core.repository.OccurrenceRepository;
import com.qb.core.repository.QuestionRepository;
import com.qb.core.service.CategoryService;
import com.qb.core.service.CompanyService;
import com.qb.core.service.RelevancyScoreService;
import com.qb.core.service.TagService;
import com.qb.core.repository.SessionRepository;
import org.springframework.ai.converter.BeanOutputConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import jakarta.annotation.PostConstruct;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates the two-step digest workflow:
 * 1. PARSE: Raw text → Gemini AI → Structured sessions + fuzzy match
 * 2. COMMIT: Admin-approved data → Database insert/link
 */
@Slf4j
@Service
public class DigestService {

    @Value("${app.claude.api-key:}")
    private String claudeApiKey;

    @Value("${app.claude.model:claude-3-5-haiku-20241022}")
    private String claudeModel;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @PostConstruct
    public void init() {
        log.info("Initialized DigestService with Claude model: {}", claudeModel);
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        // Try multiple date formats
        String[] patterns = {
                "d MMMM yyyy",    // "29th April 2026"
                "d MMM yyyy",     // "29 Apr 2026"
                "yyyy-MM-dd",     // "2026-04-29"
                "dd/MM/yyyy",     // "29/04/2026"
                "MM/dd/yyyy",     // "04/29/2026"
                "dd-MM-yyyy"      // "29-04-2026"
        };
        for (String pattern : patterns) {
            try {
                return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern(pattern));
            } catch (Exception e) {
                // try next pattern
            }
        }
        // Try to clean ordinal suffix (1st, 2nd, 3rd, 4th etc)
        try {
            String cleaned = dateStr.replaceAll("(?<=\\d)(st|nd|rd|th)\\b", "");
            return LocalDate.parse(cleaned, DateTimeFormatter.ofPattern("d MMMM yyyy"));
        } catch (Exception e) {
            log.warn("Could not parse date: {}", dateStr);
            return null;
        }
    }

    private final FuzzyMatchService fuzzyMatchService;
    private final CompanyService companyService;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final QuestionRepository questionRepo;
    private final SessionRepository sessionRepo;
    private final OccurrenceRepository occurrenceRepo;
    private final RelevancyScoreService relevancyScoreService;

    public DigestService(
            FuzzyMatchService fuzzyMatchService,
            CompanyService companyService,
            CategoryService categoryService,
            TagService tagService,
            QuestionRepository questionRepo,
            SessionRepository sessionRepo,
            OccurrenceRepository occurrenceRepo,
            RelevancyScoreService relevancyScoreService
    ) {
        this.fuzzyMatchService = fuzzyMatchService;
        this.companyService = companyService;
        this.categoryService = categoryService;
        this.tagService = tagService;
        this.questionRepo = questionRepo;
        this.sessionRepo = sessionRepo;
        this.occurrenceRepo = occurrenceRepo;
        this.relevancyScoreService = relevancyScoreService;
    }

    // ─── STEP 1: PARSE ───────────────────────────────────────────────

    /**
     * Parse raw interview text using AI and enhance with fuzzy matching.
     * This is Step 1 of the digest flow — returns preview data for admin review.
     */
    public DigestParseResponse parse(String rawText) {
        // 1. Fetch category list from DB for constrained classification
        String categoryList = String.join(", ", categoryService.getAllCategoryNames());

        // 2. Call Claude AI to extract structured data
        log.info("Calling Claude AI to parse interview text ({} chars)", rawText.length());

        BeanOutputConverter<DigestAiResponse> converter = new BeanOutputConverter<>(DigestAiResponse.class);

        DigestAiResponse aiResult = callClaude(rawText, categoryList, converter);

        // 3. Convert AI output to response DTOs + run fuzzy matching
        List<ParsedSession> sessions = new ArrayList<>();
        int tempCounter = 0;

        for (DigestAiResponse.AiSession sessionNode : aiResult.sessions()) {
            List<ParsedQuestion> questions = new ArrayList<>();

            for (DigestAiResponse.AiQuestion qNode : sessionNode.questions()) {
                tempCounter++;
                String questionText = qNode.text();

                // Fuzzy match against existing questions in DB
                ExistingMatch match = fuzzyMatchService.findBestMatch(questionText).orElse(null);

                questions.add(new ParsedQuestion(
                        "t" + tempCounter,
                        questionText,
                        qNode.category() != null ? qNode.category() : "General",
                        qNode.suggestedTags() != null ? qNode.suggestedTags() : new ArrayList<>(),
                        match
                ));
            }

            sessions.add(new ParsedSession(
                    sessionNode.candidateName() != null ? sessionNode.candidateName() : "Unknown",
                    sessionNode.company() != null ? sessionNode.company() : "Unknown",
                    sessionNode.round() != null ? sessionNode.round() : "L1",
                    sessionNode.date(),
                    sessionNode.interviewer(),
                    questions
            ));
        }

        log.info("Parsed {} sessions with {} total questions", sessions.size(), tempCounter);
        return new DigestParseResponse(sessions);
    }

    private DigestAiResponse callClaude(String rawText, String categoryList, BeanOutputConverter<DigestAiResponse> converter) {
        String systemPrompt = PromptTemplates.DIGEST_SYSTEM_PROMPT.replace("{categoryList}", categoryList);
        String fullPrompt = systemPrompt + "\n\nHere is the interview text to parse. Respond ONLY with valid JSON matching the required schema:\n" + rawText
                + "\n\n" + converter.getFormat();

        Map<String, Object> body = Map.of(
                "model", claudeModel,
                "max_tokens", 2000,
                "messages", List.of(Map.of("role", "user", "content", fullPrompt))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", claudeApiKey);
        headers.set("anthropic-version", "2023-06-01");

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                "https://api.anthropic.com/v1/messages",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                JsonNode.class
        );

        String content = response.getBody().path("content").get(0).path("text").asText();
        // Strip markdown fences if present
        content = content.replaceAll("(?s)```json\\s*", "").replaceAll("```", "").trim();
        return converter.convert(content);
    }

    // ─── STEP 2: COMMIT ──────────────────────────────────────────────

    /**
     * Commit admin-approved digest data to the database.
     * Creates companies, sessions, questions, tags, and occurrences.
     */
    @Caching(evict = {
            @CacheEvict(value = "questions",       allEntries = true),
            @CacheEvict(value = "question",        allEntries = true),
            @CacheEvict(value = "companies",       allEntries = true),
            @CacheEvict(value = "tags",             allEntries = true),
            @CacheEvict(value = "sessions",        allEntries = true),
            @CacheEvict(value = "admin-dashboard", allEntries = true)
    })
    @Transactional
    public DigestCommitResponse commit(DigestCommitRequest request) {
        AtomicInteger sessionsCreated = new AtomicInteger(0);
        AtomicInteger questionsCreated = new AtomicInteger(0);
        AtomicInteger questionsLinked = new AtomicInteger(0);
        AtomicInteger tagsCreated = new AtomicInteger(0);

        for (CommitSession cs : request.sessions()) {
            // 1. Find or create company
            Company company = companyService.findOrCreateByName(cs.companyName());

            // 2. Create interview session
            InterviewSession session = InterviewSession.builder()
                    .candidateName(cs.candidateName())
                    .candidateId(cs.candidateId())
                    .company(company)
                    .round(cs.round().toLowerCase())
                    .interviewDate(cs.date() != null ? parseDate(cs.date()) : null)
                    .interviewerName(cs.interviewerName())
                    .build();
            sessionRepo.save(session);
            sessionsCreated.incrementAndGet();

            // 3. Process each question
            for (CommitQuestion cq : cs.questions()) {
                Question question;

                if (cq.existingQuestionId() != null) {
                    // Link to existing canonical question
                    question = questionRepo.findById(cq.existingQuestionId())
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Question not found: " + cq.existingQuestionId()));
                    questionsLinked.incrementAndGet();
                } else {
                    // Create new canonical question
                    Set<Tag> tags = new HashSet<>();
                    if (cq.tags() != null) {
                        for (String tagName : cq.tags()) {
                            Tag tag = tagService.findOrCreate(tagName);
                            tags.add(tag);
                        }
                    }

                    // Resolve category — validate against DB, fallback to General
                    com.qb.core.entity.Category category = categoryService.findOrCreateByName(cq.category());

                    question = Question.builder()
                            .text(cq.text())
                            .category(category)
                            .tags(tags)
                            .occurrenceCount(0)
                            .build();
                    questionRepo.save(question);
                    questionsCreated.incrementAndGet();
                    tagsCreated.addAndGet((int) tags.stream()
                            .filter(t -> t.getId() == null) // Newly created
                            .count());
                }

                // 4. Create occurrence (links question ↔ session)
                question.incrementOccurrenceCount();
                questionRepo.save(question);

                QuestionOccurrence occurrence = QuestionOccurrence.builder()
                        .question(question)
                        .session(session)
                        .build();
                occurrenceRepo.save(occurrence);
            }
        }

        log.info("Digest committed: {} sessions, {} new questions, {} linked, {} tags",
                sessionsCreated.get(), questionsCreated.get(), questionsLinked.get(), tagsCreated.get());

        // Recompute relevancy scores for all questions since occurrence counts changed
        relevancyScoreService.recomputeAll();

        return new DigestCommitResponse(
                sessionsCreated.get(),
                questionsCreated.get(),
                questionsLinked.get(),
                tagsCreated.get()
        );
    }
}
