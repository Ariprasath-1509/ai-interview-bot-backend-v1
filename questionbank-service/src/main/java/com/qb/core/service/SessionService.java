package com.qb.core.service;

import com.qb.core.dto.SessionDTO;
import com.qb.core.entity.InterviewSession;
import com.qb.core.exception.ResourceNotFoundException;
import com.qb.core.repository.OccurrenceRepository;
import com.qb.core.repository.QuestionRepository;
import com.qb.core.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository     sessionRepo;
    private final OccurrenceRepository  occurrenceRepo;
    private final QuestionRepository    questionRepo;
    private final RelevancyScoreService relevancyScoreService;

    @Cacheable(value = "sessions", key = "'all'")
    @Transactional(readOnly = true)
    public List<SessionDTO> getAllSessions() {
        return sessionRepo.findAllWithCompany().stream()
                .map(this::toDTO)
                .toList();
    }

    @Caching(evict = {
            @CacheEvict(value = "sessions",        allEntries = true),
            @CacheEvict(value = "questions",       allEntries = true),
            @CacheEvict(value = "admin-dashboard", allEntries = true)
    })
    @Transactional
    public void deleteSession(UUID id) {
        InterviewSession session = sessionRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Session", id));

        // Decrement occurrence_count on all questions in this session
        occurrenceRepo.findBySessionId(id).forEach(occ -> {
            var q = occ.getQuestion();
            if (q.getOccurrenceCount() != null && q.getOccurrenceCount() > 0) {
                q.setOccurrenceCount(q.getOccurrenceCount() - 1);
                questionRepo.save(q);
            }
        });

        sessionRepo.delete(session);

        // Recompute relevancy since occurrence counts changed
        relevancyScoreService.recomputeAll();
    }

    private SessionDTO toDTO(InterviewSession s) {
        return new SessionDTO(
                s.getId(),
                s.getCandidateName(),
                s.getCandidateId(),
                s.getCompany().getName(),
                s.getCompany().getSlug(),
                s.getRound(),
                s.getInterviewDate(),
                s.getInterviewerName()
        );
    }
}
