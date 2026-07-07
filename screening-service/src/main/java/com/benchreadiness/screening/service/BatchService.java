package com.benchreadiness.screening.service;

import com.benchreadiness.screening.dto.CreateBatchRequest;
import com.benchreadiness.screening.entity.ScreeningBatch;
import com.benchreadiness.screening.entity.ScreeningCandidate;
import com.benchreadiness.screening.entity.ScreeningQuestion;
import com.benchreadiness.screening.entity.enums.BatchStatus;
import com.benchreadiness.screening.entity.enums.CandidateStage;
import com.benchreadiness.screening.mail.ScreeningMailService;
import com.benchreadiness.screening.repository.ScreeningBatchRepository;
import com.benchreadiness.screening.repository.ScreeningCandidateRepository;
import com.benchreadiness.screening.repository.ScreeningQuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class BatchService {

    private final ScreeningBatchRepository batchRepository;
    private final ScreeningCandidateRepository candidateRepository;
    private final ScreeningQuestionRepository questionRepository;
    private final QuestionGenerationService questionGenerationService;
    private final ScreeningMailService mailService;
    private final SecureRandom secureRandom = new SecureRandom();

    public BatchService(ScreeningBatchRepository batchRepository,
                        ScreeningCandidateRepository candidateRepository,
                        ScreeningQuestionRepository questionRepository,
                        QuestionGenerationService questionGenerationService,
                        ScreeningMailService mailService) {
        this.batchRepository = batchRepository;
        this.candidateRepository = candidateRepository;
        this.questionRepository = questionRepository;
        this.questionGenerationService = questionGenerationService;
        this.mailService = mailService;
    }

    @Transactional
    public ScreeningBatch createBatch(CreateBatchRequest req, String assignerUserId, String assignerEmail, String assignerName) throws Exception {
        ScreeningBatch batch = new ScreeningBatch();
        batch.setLanguage(req.getLanguage());
        batch.setConceptScope(QuestionGenerationService.scopeFor(req.getLanguage()));
        batch.setDeadline(req.getDeadline());
        batch.setAssignerUserId(assignerUserId);
        batch.setAssignerEmail(assignerEmail);
        batch.setAssignerName(assignerName);
        batch = batchRepository.save(batch);

        List<ScreeningQuestion> questions = questionGenerationService.generate(batch);
        questionRepository.saveAll(questions);

        List<ScreeningCandidate> candidates = new ArrayList<>();
        for (CreateBatchRequest.CandidateEntry entry : req.getCandidates()) {
            ScreeningCandidate candidate = new ScreeningCandidate();
            candidate.setBatch(batch);
            candidate.setName(entry.getName());
            candidate.setEmail(entry.getEmail());
            candidate.setContactNumber(entry.getContactNumber());
            candidate.setInstitute(entry.getInstitute());
            candidate.setBranch(entry.getBranch());
            candidate.setYop(entry.getYop());
            candidate.setExperience(entry.getExperience());
            candidate.setToken(generateToken());
            candidate.setShuffleSeed(ThreadLocalRandom.current().nextLong());
            candidate.setStage(CandidateStage.ROUND1_PENDING);
            candidates.add(candidateRepository.save(candidate));
        }

        ScreeningBatch savedBatch = batch;
        candidates.forEach(c -> mailService.sendCandidateInvite(c, savedBatch));

        return batch;
    }

    private String generateToken() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Transactional
    public ScreeningBatch updateDeadline(String batchId, Instant newDeadline) {
        ScreeningBatch batch = getBatchOrThrow(batchId);
        if (batch.getStatus() != BatchStatus.OPEN) {
            throw new IllegalStateException("Only an open batch's deadline can be changed");
        }
        batch.setDeadline(newDeadline);
        return batchRepository.save(batch);
    }

    @Transactional
    public ScreeningCandidate addCandidate(String batchId, CreateBatchRequest.CandidateEntry entry) {
        ScreeningBatch batch = getBatchOrThrow(batchId);
        if (batch.getStatus() != BatchStatus.OPEN) {
            throw new IllegalStateException("Cannot add a candidate to a closed batch");
        }
        ScreeningCandidate candidate = new ScreeningCandidate();
        candidate.setBatch(batch);
        candidate.setName(entry.getName());
        candidate.setEmail(entry.getEmail());
        candidate.setContactNumber(entry.getContactNumber());
        candidate.setInstitute(entry.getInstitute());
        candidate.setBranch(entry.getBranch());
        candidate.setYop(entry.getYop());
        candidate.setExperience(entry.getExperience());
        candidate.setToken(generateToken());
        candidate.setShuffleSeed(ThreadLocalRandom.current().nextLong());
        candidate.setStage(CandidateStage.ROUND1_PENDING);
        candidate = candidateRepository.save(candidate);
        mailService.sendCandidateInvite(candidate, batch);
        return candidate;
    }

    @Transactional
    public void removeCandidate(String candidateId) {
        ScreeningCandidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new NoSuchElementException("Candidate not found"));
        if (candidate.getStage() != CandidateStage.ROUND1_PENDING) {
            throw new IllegalStateException("Cannot remove a candidate who has already started Round 1");
        }
        candidateRepository.delete(candidate);
    }

    @Transactional
    public void deleteBatch(String batchId) {
        ScreeningBatch batch = getBatchOrThrow(batchId);
        long total = candidateRepository.countByBatchId(batchId);
        long notStarted = candidateRepository.countByBatchIdAndStageIn(batchId, List.of(CandidateStage.ROUND1_PENDING));
        if (total != notStarted) {
            throw new IllegalStateException("Cannot delete a batch once a candidate has started Round 1");
        }
        // screening_questions/screening_candidates/screening_answers cascade at the DB level (ON DELETE CASCADE).
        batchRepository.delete(batch);
    }

    private ScreeningBatch getBatchOrThrow(String batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new NoSuchElementException("Batch not found"));
    }
}
