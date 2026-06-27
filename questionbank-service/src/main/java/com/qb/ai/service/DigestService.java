package com.qb.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qb.ai.dto.*;
import com.qb.ai.dto.DigestCommitRequest.CommitQuestion;
import com.qb.ai.dto.DigestCommitRequest.CommitSession;
import com.qb.ai.dto.DigestParseResponse.*;
import com.qb.client.AiServiceClient;
import com.qb.core.entity.*;
import com.qb.core.repository.OccurrenceRepository;
import com.qb.core.repository.QuestionRepository;
import com.qb.core.service.CategoryService;
import com.qb.core.service.CompanyService;
import com.qb.core.service.RelevancyScoreService;
import com.qb.core.service.TagService;
import com.qb.core.repository.SessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates the two-step digest workflow:
 * 1. PARSE  — forwards raw text + categories to ai-service for LLM parsing
 * 2. COMMIT — saves admin-approved data to the questionbank DB
 */
@Slf4j
@Service
public class DigestService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiServiceClient aiServiceClient;
    private final FuzzyMatchService fuzzyMatchService;
    private final CompanyService companyService;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final QuestionRepository questionRepo;
    private final SessionRepository sessionRepo;
    private final OccurrenceRepository occurrenceRepo;
    private final RelevancyScoreService relevancyScoreService;

    public DigestService(
            AiServiceClient aiServiceClient,
            FuzzyMatchService fuzzyMatchService,
            CompanyService companyService,
            CategoryService categoryService,
            TagService tagService,
            QuestionRepository questionRepo,
            SessionRepository sessionRepo,
            OccurrenceRepository occurrenceRepo,
            RelevancyScoreService relevancyScoreService
    ) {
        this.aiServiceClient = aiServiceClient;
        this.fuzzyMatchService = fuzzyMatchService;
        this.companyService = companyService;
        this.categoryService = categoryService;
        this.tagService = tagService;
        this.questionRepo = questionRepo;
        this.sessionRepo = sessionRepo;
        this.occurrenceRepo = occurrenceRepo;
        this.relevancyScoreService = relevancyScoreService;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        String[] patterns = {
                "d MMMM yyyy", "d MMM yyyy", "yyyy-MM-dd",
                "dd/MM/yyyy", "MM/dd/yyyy", "dd-MM-yyyy"
        };
        for (String pattern : patterns) {
            try {
                return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern(pattern));
            } catch (Exception ignored) {}
        }
        try {
            String cleaned = dateStr.replaceAll("(?<=\\d)(st|nd|rd|th)\\b", "");
            return LocalDate.parse(cleaned, DateTimeFormatter.ofPattern("d MMMM yyyy"));
        } catch (Exception e) {
            log.warn("Could not parse date: {}", dateStr);
            return null;
        }
    }

    // ─── STEP 1: PARSE ───────────────────────────────────────────────

    /**
     * Delegates LLM parsing to ai-service, then enhances the result with
     * fuzzy matching against existing questions.
     */
    public DigestParseResponse parse(String rawText) {
        String categoryList = String.join(", ", categoryService.getAllCategoryNames());
        log.info("[Digest] Delegating LLM parse to ai-service ({} chars, categories: {})",
                rawText.length(), categoryList);

        // Call ai-service — it owns all LLM keys and provider routing
        Map<String, String> requestBody = Map.of(
                "rawText", rawText,
                "categoryList", categoryList
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> response = aiServiceClient.digestParse(requestBody);

        if (!Boolean.TRUE.equals(response.get("success"))) {
            String msg = (String) response.getOrDefault("message", "AI service returned failure");
            log.error("[Digest] ai-service digest-parse failed: {}", msg);
            throw new RuntimeException("AI parsing failed: " + msg);
        }

        // Deserialize the nested DigestAiResponse
        DigestAiResponse aiResult;
        try {
            Object data = response.get("data");
            String json = objectMapper.writeValueAsString(data);
            aiResult = objectMapper.readValue(json, DigestAiResponse.class);
        } catch (Exception e) {
            log.error("[Digest] Failed to deserialize ai-service response: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse AI response: " + e.getMessage(), e);
        }

        // Enhance with fuzzy matching + category normalization
        List<String> allowedCategories = categoryService.getAllCategoryNames();
        List<ParsedSession> sessions = new ArrayList<>();
        int tempCounter = 0;

        for (DigestAiResponse.AiSession sessionNode : aiResult.sessions()) {
            List<ParsedQuestion> questions = new ArrayList<>();

            for (DigestAiResponse.AiQuestion qNode : sessionNode.questions()) {
                tempCounter++;
                String questionText = qNode.text();
                String category = categoryService.normalizeCategoryName(qNode.category(), allowedCategories);

                ExistingMatch match = fuzzyMatchService.findBestMatch(questionText, category).orElse(null);

                questions.add(new ParsedQuestion(
                        "t" + tempCounter,
                        questionText,
                        category,
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

        log.info("[Digest] Parsed {} sessions, {} total questions", sessions.size(), tempCounter);
        return new DigestParseResponse(sessions);
    }

    // ─── STEP 1b: CHUNKED PARSE ──────────────────────────────────────

    /**
     * For large documents: splits the text into chunks, parses each via ai-service,
     * and merges all resulting sessions into a single DigestParseResponse.
     */
    public DigestParseResponse parseChunked(String fullText) {
        List<String> chunks = chunkText(fullText, 3500);
        log.info("[Digest] Chunking {} chars into {} chunks", fullText.length(), chunks.size());

        String categoryList = String.join(", ", categoryService.getAllCategoryNames());
        List<String> allowedCategories = categoryService.getAllCategoryNames();
        List<ParsedSession> allSessions = new ArrayList<>();
        int tempCounter = 0;

        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            log.info("[Digest] Processing chunk {}/{} ({} chars)", i + 1, chunks.size(), chunk.length());
            try {
                Map<String, String> requestBody = Map.of("rawText", chunk, "categoryList", categoryList);
                @SuppressWarnings("unchecked")
                Map<String, Object> response = aiServiceClient.digestParse(requestBody);

                if (!Boolean.TRUE.equals(response.get("success"))) {
                    log.warn("[Digest] Chunk {}/{} failed: {}", i + 1, chunks.size(), response.get("message"));
                    continue;
                }

                DigestAiResponse aiResult;
                try {
                    Object data = response.get("data");
                    String json = objectMapper.writeValueAsString(data);
                    aiResult = objectMapper.readValue(json, DigestAiResponse.class);
                } catch (Exception e) {
                    log.warn("[Digest] Chunk {}/{} deserialize failed: {}", i + 1, chunks.size(), e.getMessage());
                    continue;
                }

                for (DigestAiResponse.AiSession sessionNode : aiResult.sessions()) {
                    List<ParsedQuestion> questions = new ArrayList<>();
                    for (DigestAiResponse.AiQuestion qNode : sessionNode.questions()) {
                        tempCounter++;
                        String questionText = qNode.text();
                        String category = categoryService.normalizeCategoryName(qNode.category(), allowedCategories);
                        ExistingMatch match = fuzzyMatchService.findBestMatch(questionText, category).orElse(null);
                        questions.add(new ParsedQuestion(
                                "t" + tempCounter,
                                questionText,
                                category,
                                qNode.suggestedTags() != null ? qNode.suggestedTags() : new ArrayList<>(),
                                match
                        ));
                    }
                    allSessions.add(new ParsedSession(
                            sessionNode.candidateName() != null ? sessionNode.candidateName() : "Unknown",
                            sessionNode.company() != null ? sessionNode.company() : "Unknown",
                            sessionNode.round() != null ? sessionNode.round() : "L1",
                            sessionNode.date(),
                            sessionNode.interviewer(),
                            questions
                    ));
                }
            } catch (Exception e) {
                log.warn("[Digest] Chunk {}/{} exception: {}", i + 1, chunks.size(), e.getMessage());
            }
        }

        log.info("[Digest] Chunked parse complete: {} sessions total", allSessions.size());
        return new DigestParseResponse(allSessions);
    }

    private List<String> chunkText(String text, int maxChars) {
        // Normalize line endings
        text = text.replace("\r\n", "\n").replace("\r", "\n");

        String[] lines = text.split("\n");
        List<String> chunks = new ArrayList<>();

        java.util.regex.Pattern boundaryPattern = java.util.regex.Pattern.compile(
                "(?i)^(interview|candidate[:\\s]|company[:\\s]|round[:\\s]|date[:\\s]|name[:\\s])" +
                "|^[A-Z][A-Z\\s]{18,}$"  // all-caps header lines
        );

        int boundaryCount = 0;
        for (String line : lines) {
            if (boundaryPattern.matcher(line.trim()).find() && !line.trim().isEmpty()) {
                boundaryCount++;
            }
        }

        if (boundaryCount >= 2) {
            // Boundary-based splitting: flush a new chunk at each detected session header
            StringBuilder current = new StringBuilder();
            for (String line : lines) {
                boolean isBoundary = boundaryPattern.matcher(line.trim()).find() && !line.trim().isEmpty();
                if (isBoundary && current.length() > 200) {
                    String segment = current.toString().trim();
                    if (!segment.isEmpty()) {
                        flushSegment(segment, maxChars, chunks);
                    }
                    current = new StringBuilder();
                }
                current.append(line).append("\n");
            }
            String remaining = current.toString().trim();
            if (!remaining.isEmpty()) {
                flushSegment(remaining, maxChars, chunks);
            }
        } else {
            // Paragraph-based splitting: group double-newline-separated paragraphs
            String[] paragraphs = text.split("\n\n+");
            StringBuilder current = new StringBuilder();
            for (String para : paragraphs) {
                if (current.length() + para.length() + 2 > maxChars && current.length() > 0) {
                    chunks.add(current.toString().trim());
                    current = new StringBuilder();
                }
                current.append(para).append("\n\n");
            }
            String remaining = current.toString().trim();
            if (!remaining.isEmpty()) {
                chunks.add(remaining);
            }
        }

        return chunks.isEmpty() ? List.of(text) : chunks;
    }

    private void flushSegment(String segment, int maxChars, List<String> chunks) {
        if (segment.length() <= maxChars) {
            chunks.add(segment);
            return;
        }
        // Hard split with 150-char overlap
        int start = 0;
        while (start < segment.length()) {
            int end = Math.min(start + maxChars, segment.length());
            if (end < segment.length()) {
                int lastNewline = segment.lastIndexOf('\n', end);
                if (lastNewline > start + 500) {
                    end = lastNewline;
                }
            }
            chunks.add(segment.substring(start, end).trim());
            start = end - 150;
            if (start < 0) start = 0;
        }
    }

    // ─── STEP 2: COMMIT ──────────────────────────────────────────────

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
            Company company = companyService.findOrCreateByName(cs.companyName());

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

            for (CommitQuestion cq : cs.questions()) {
                Question question;
                String categoryName = categoryService.normalizeCategoryName(
                        cq.category(), categoryService.getAllCategoryNames());

                UUID linkId = cq.existingQuestionId();
                if (linkId != null && !fuzzyMatchService.verifyLinkAllowed(linkId, cq.text(), categoryName)) {
                    log.warn("Ignoring existingQuestionId {} — similarity too low for '{}'", linkId, cq.text());
                    linkId = null;
                }

                if (linkId != null) {
                    UUID finalLinkId = linkId;
                    question = questionRepo.findById(linkId)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Question not found: " + finalLinkId));
                    questionsLinked.incrementAndGet();
                } else {
                    Set<Tag> tags = new HashSet<>();
                    if (cq.tags() != null) {
                        for (String tagName : cq.tags()) {
                            Tag tag = tagService.findOrCreate(tagName);
                            tags.add(tag);
                        }
                    }

                    Category category = categoryService.resolveKnownCategory(categoryName);

                    question = Question.builder()
                            .text(cq.text())
                            .category(category)
                            .tags(tags)
                            .occurrenceCount(0)
                            .questionType(inferQuestionType(cq.text(), category.getName(), cq.tags()))
                            .build();
                    questionRepo.save(question);
                    questionsCreated.incrementAndGet();
                    tagsCreated.addAndGet((int) tags.stream()
                            .filter(t -> t.getId() == null)
                            .count());
                }

                question.incrementOccurrenceCount();
                questionRepo.save(question);

                QuestionOccurrence occurrence = QuestionOccurrence.builder()
                        .question(question)
                        .session(session)
                        .build();
                occurrenceRepo.save(occurrence);
            }
        }

        log.info("[Digest] Committed: {} sessions, {} new questions, {} linked, {} tags",
                sessionsCreated.get(), questionsCreated.get(), questionsLinked.get(), tagsCreated.get());

        relevancyScoreService.recomputeAll();

        return new DigestCommitResponse(
                sessionsCreated.get(),
                questionsCreated.get(),
                questionsLinked.get(),
                tagsCreated.get()
        );
    }

    private static String inferQuestionType(String text, String categoryName, List<String> tags) {
        if ("DSA".equalsIgnoreCase(categoryName)) return "CODING";
        if (tags != null && tags.stream().anyMatch(t -> t != null && t.toLowerCase().matches(".*(coding|algorithm|dsa|leetcode).*"))) {
            return "CODING";
        }
        if (text != null) {
            String lower = text.toLowerCase();
            if (lower.contains("write a function") || lower.contains("implement") || lower.contains("algorithm")
                    || lower.contains("time complexity") || lower.contains("given an array")) {
                return "CODING";
            }
            if (lower.contains("tell me about a time") || lower.contains("behavioral") || lower.contains("team conflict")) {
                return "BEHAVIORAL";
            }
        }
        return "TECHNICAL";
    }
}
