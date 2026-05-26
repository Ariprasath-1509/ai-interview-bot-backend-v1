package com.qb.core.service;

import com.qb.core.dto.QuestionDTO;
import com.qb.core.dto.QuestionUpdateRequest;
import com.qb.core.entity.Category;
import com.qb.core.entity.Question;
import com.qb.core.entity.Tag;
import com.qb.core.exception.ResourceNotFoundException;
import com.qb.core.repository.CategoryRepository;
import com.qb.core.repository.CompanyRepository;
import com.qb.core.repository.OccurrenceRepository;
import com.qb.core.repository.QuestionRepository;
import com.qb.core.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionRepository    questionRepo;
    private final TagRepository         tagRepo;
    private final OccurrenceRepository  occurrenceRepo;
    private final CompanyRepository     companyRepo;
    private final CategoryRepository    categoryRepo;
    private final RelevancyScoreService relevancyScoreService;

    /**
     * Search + filter questions with pagination.
     * Sorted by relevancy_score DESC at DB level.
     * Importance filter applied at DB level via relevancy_label column.
     *
     * Performance: Only cache structured filter queries (not free-text search)
     * since the key space for search text is unbounded.
     */
    @Cacheable(
            value = "questions",
            key = "#search + '::' + #interviewType + '::' + #category + '::' + #companySlug + '::' + #round + '::' + #tagName + '::' + #importanceLabel + '::p' + #page + '::s' + #size",
            condition = "#search == null || #search.isBlank()"
    )
    @Transactional(readOnly = true)
    public Page<QuestionDTO> searchQuestions(String search, String interviewType, String category,
                                             String companySlug, String round, String tagName,
                                             String importanceLabel, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);

        Page<Question> questionPage = questionRepo.searchQuestions(
                blankToNull(search), blankToNull(interviewType), blankToNull(category),
                blankToNull(companySlug), blankToNull(round),
                blankToNull(tagName), blankToNull(importanceLabel),
                pageable
        );

        // ── Batch-load occurrence data for all questions in this page ──
        // Eliminates N+1 queries: instead of 2 queries per question (companies + sessions),
        // we do 2 queries total for the entire page.
        List<UUID> questionIds = questionPage.getContent().stream()
                .map(Question::getId)
                .toList();

        // Batch: company names per question
        Map<UUID, List<String>> companiesByQuestion = batchLoadCompanies(questionIds);

        // Batch: session info per question
        Map<UUID, List<QuestionDTO.SessionInfo>> sessionsByQuestion =
                batchLoadSessionInfo(questionIds, blankToNull(companySlug), blankToNull(round));

        return questionPage.map(q -> toDTOBatched(q, companiesByQuestion, sessionsByQuestion));
    }

    /**
     * Get single question with full details.
     */
    @Cacheable(value = "question", key = "#id")
    @Transactional(readOnly = true)
    public QuestionDTO getById(UUID id) {
        Question q = questionRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question", id));
        return toDTO(q, null, null);
    }

    /**
     * Update question (admin only).
     */
    @Caching(evict = {
            @CacheEvict(value = "questions", allEntries = true),
            @CacheEvict(value = "question",  key = "#id")
    })
    @Transactional
    public QuestionDTO updateQuestion(UUID id, QuestionUpdateRequest request) {
        Question q = questionRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question", id));

        q.setText(request.text());

        // Resolve category by name
        Category category = categoryRepo.findByNameIgnoreCase(request.category())
                .orElseGet(() -> categoryRepo.findByNameIgnoreCase("General").orElseThrow());
        q.setCategory(category);

        if (request.tags() != null) {
            Set<Tag> tags = request.tags().stream()
                    .map(name -> tagRepo.findByNameIgnoreCase(name)
                            .orElseGet(() -> tagRepo.save(Tag.builder().name(name.toLowerCase()).build())))
                    .collect(Collectors.toSet());
            q.setTags(tags);
        }

        return toDTO(questionRepo.save(q), null, null);
    }

    /**
     * Delete question (admin only).
     */
    @Caching(evict = {
            @CacheEvict(value = "questions", allEntries = true),
            @CacheEvict(value = "question",  key = "#id")
    })
    @Transactional
    public void deleteQuestion(UUID id) {
        if (!questionRepo.existsById(id)) {
            throw new ResourceNotFoundException("Question", id);
        }
        questionRepo.deleteById(id);
    }

    /**
     * Get questions asked by a specific company.
     */
    @Transactional(readOnly = true)
    public List<QuestionDTO> getByCompanySlug(String slug) {
        return questionRepo.findByCompanySlug(slug).stream()
                .map(q -> toDTO(q, slug, null))
                .toList();
    }

    /**
     * Get questions by company and interview mode.
     * Used by interview-service to fetch questions at interview creation.
     */
    @Cacheable(
            value = "questionsByCompanyMode",
            key = "#company + '::' + #mode"
    )
    @Transactional(readOnly = true)
    public List<QuestionDTO> getQuestionsByCompanyAndMode(String company, String mode) {
        return questionRepo.findByCompanyAndRound(company, mode).stream()
                .map(q -> toDTO(q, company, mode))
                .toList();
    }

    /**
     * Lightweight flat list for interview creation question picker.
     * Reuses the existing search query but returns a simple list capped at `size`.
     */
    @Transactional(readOnly = true)
    public List<QuestionDTO> getQuestionsForInterview(String search, String category, String importance, int size) {
        int cappedSize = Math.min(size, 200);
        PageRequest pageable = PageRequest.of(0, cappedSize);
        Page<Question> page = questionRepo.searchQuestions(
                blankToNull(search), null, blankToNull(category),
                null, null, null, blankToNull(importance), pageable
        );
        List<UUID> ids = page.getContent().stream().map(Question::getId).toList();
        Map<UUID, List<String>> companies = batchLoadCompanies(ids);
        Map<UUID, List<QuestionDTO.SessionInfo>> sessions = batchLoadSessionInfo(ids, null, null);
        return page.getContent().stream()
                .map(q -> toDTOBatched(q, companies, sessions))
                .toList();
    }

    // ── Batch loading helpers (N+1 elimination) ──

    /**
     * Batch-load company names for multiple questions in a single query.
     */
    private Map<UUID, List<String>> batchLoadCompanies(List<UUID> questionIds) {
        if (questionIds.isEmpty()) return Map.of();

        List<Object[]> rows = occurrenceRepo.findCompanyNamesByQuestionIds(questionIds);
        Map<UUID, List<String>> result = new HashMap<>();
        for (Object[] row : rows) {
            UUID qId = (UUID) row[0];
            String companyName = (String) row[1];
            result.computeIfAbsent(qId, k -> new ArrayList<>()).add(companyName);
        }
        return result;
    }

    /**
     * Batch-load session info for multiple questions in a single query.
     */
    private Map<UUID, List<QuestionDTO.SessionInfo>> batchLoadSessionInfo(
            List<UUID> questionIds, String companySlug, String round) {
        if (questionIds.isEmpty()) return Map.of();

        List<Object[]> rows = occurrenceRepo.findSessionInfoByQuestionIds(
                questionIds, companySlug, round);
        Map<UUID, List<QuestionDTO.SessionInfo>> result = new HashMap<>();
        for (Object[] row : rows) {
            UUID qId = (UUID) row[0];
            QuestionDTO.SessionInfo info = new QuestionDTO.SessionInfo(
                    (String) row[1],
                    (String) row[2],
                    row[3] != null ? ((java.sql.Date) row[3]).toLocalDate() : null
            );
            result.computeIfAbsent(qId, k -> new ArrayList<>()).add(info);
        }
        return result;
    }

    // ── DTO Mapping (batched — used in page results) ──

    private QuestionDTO toDTOBatched(Question q,
                                     Map<UUID, List<String>> companiesByQuestion,
                                     Map<UUID, List<QuestionDTO.SessionInfo>> sessionsByQuestion) {
        List<String> tagNames = q.getTags().stream()
                .map(Tag::getName)
                .sorted()
                .toList();

        List<String> companies = companiesByQuestion.getOrDefault(q.getId(), List.of());
        List<QuestionDTO.SessionInfo> sessions = sessionsByQuestion.getOrDefault(q.getId(), List.of());

        return new QuestionDTO(
                q.getId(),
                q.getText(),
                q.getCategory() != null ? q.getCategory().getId() : null,
                q.getCategory() != null ? q.getCategory().getName() : null,
                q.getCategory() != null ? q.getCategory().getInterviewType() : null,
                q.getOccurrenceCount() != null ? q.getOccurrenceCount() : 0,
                tagNames,
                companies,
                sessions,
                q.getRelevancyLabel(),
                q.getCreatedAt(),
                q.getUpdatedAt()
        );
    }

    // ── DTO Mapping (single-question — used for getById, update) ──

    private QuestionDTO toDTO(Question q, String companySlug, String round) {
        List<String> tagNames = q.getTags().stream()
                .map(Tag::getName)
                .sorted()
                .toList();

        List<String> companies = occurrenceRepo.findCompanyNamesByQuestionId(q.getId());

        List<QuestionDTO.SessionInfo> sessions = occurrenceRepo
                .findSessionInfoByQuestionId(q.getId(), companySlug, round)
                .stream()
                .map(row -> new QuestionDTO.SessionInfo(
                        (String) row[0],
                        (String) row[1],
                        row[2] != null ? ((java.sql.Date) row[2]).toLocalDate() : null
                ))
                .toList();

        return new QuestionDTO(
                q.getId(),
                q.getText(),
                q.getCategory() != null ? q.getCategory().getId() : null,
                q.getCategory() != null ? q.getCategory().getName() : null,
                q.getCategory() != null ? q.getCategory().getInterviewType() : null,
                q.getOccurrenceCount() != null ? q.getOccurrenceCount() : 0,
                tagNames,
                companies,
                sessions,
                q.getRelevancyLabel(),
                q.getCreatedAt(),
                q.getUpdatedAt()
        );
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
