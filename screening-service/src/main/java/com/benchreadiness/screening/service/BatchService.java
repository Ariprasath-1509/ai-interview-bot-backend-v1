package com.benchreadiness.screening.service;

import com.benchreadiness.screening.dto.CreateBatchFromDocumentRequest;
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

        createCandidatesAndInvite(batch, req.getCandidates());
        return batch;
    }

    /**
     * Generates the question set from a JD or an existing question paper instead of the fixed language
     * preset — same batch/candidate creation as {@link #createBatch}, different question-generation call.
     */
    @Transactional
    public ScreeningBatch createBatchFromDocument(CreateBatchFromDocumentRequest req,
                                                  String assignerUserId, String assignerEmail, String assignerName) throws Exception {
        ScreeningBatch batch = new ScreeningBatch();
        batch.setLanguage(req.getLanguage());
        batch.setConceptScope(req.getMode() == CreateBatchFromDocumentRequest.Mode.JD
                ? "Generated from an uploaded job description"
                : "Imported from an existing question paper");
        batch.setDeadline(req.getDeadline());
        batch.setAssignerUserId(assignerUserId);
        batch.setAssignerEmail(assignerEmail);
        batch.setAssignerName(assignerName);
        batch = batchRepository.save(batch);

        List<ScreeningQuestion> questions = req.getMode() == CreateBatchFromDocumentRequest.Mode.JD
                ? questionGenerationService.generateFromJd(batch, req.getDocumentText())
                : questionGenerationService.generateFromQuestionPaper(batch, req.getDocumentText());
        questionRepository.saveAll(questions);

        createCandidatesAndInvite(batch, req.getCandidates());
        return batch;
    }

    private List<ScreeningCandidate> createCandidatesAndInvite(ScreeningBatch batch, List<CreateBatchRequest.CandidateEntry> entries) {
        List<ScreeningCandidate> candidates = new ArrayList<>();
        for (CreateBatchRequest.CandidateEntry entry : entries) {
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
        candidates.forEach(c -> mailService.sendCandidateInvite(c, batch));
        return candidates;
    }

    /** Marks a placeholder batch created only to hang a direct-to-Round-2 candidate off of — excluded from the main batches list. */
    public static final String DIRECT_ENTRY_LANGUAGE = "Direct Entry (Round 2)";

    /**
     * A candidate who missed/skipped Round 1 (e.g. showed up late) — added straight into the Round 2
     * queue with no written test. Needs a batch row to hang off of (batch_id is required), so this
     * creates a minimal placeholder batch pre-marked REPORTED so the Round 1 scheduled jobs
     * (ReportingService/PurgeService) never touch it or email the assigner about a "written round"
     * that never happened.
     */
    @Transactional
    public ScreeningCandidate addDirectToRound2(CreateBatchRequest.CandidateEntry entry,
                                                String assignerUserId, String assignerEmail, String assignerName) {
        ScreeningBatch batch = new ScreeningBatch();
        batch.setLanguage(DIRECT_ENTRY_LANGUAGE);
        batch.setDeadline(Instant.now());
        batch.setAssignerUserId(assignerUserId);
        batch.setAssignerEmail(assignerEmail);
        batch.setAssignerName(assignerName);
        batch.setStatus(BatchStatus.REPORTED);
        batch = batchRepository.save(batch);

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
        candidate.setStage(CandidateStage.ROUND1_PASSED);
        return candidateRepository.save(candidate);
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

    // Safe to hard-delete from here: not yet started (Round 1), or sitting in the Round 2 queue with
    // no recorded decision yet. Once a Round 2 decision (Selected/Hold/Rejected) or anything in Round 3
    // is recorded, that's real historical data — removal from there isn't offered here.
    private static final List<CandidateStage> REMOVABLE_STAGES = List.of(
            CandidateStage.ROUND1_PENDING, CandidateStage.ROUND1_PASSED, CandidateStage.ROUND2_IN_PROGRESS);

    @Transactional
    public void removeCandidate(String candidateId) {
        ScreeningCandidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new NoSuchElementException("Candidate not found"));
        if (!REMOVABLE_STAGES.contains(candidate.getStage())) {
            throw new IllegalStateException("Cannot remove a candidate who already has a recorded Round 2/3 decision");
        }
        candidateRepository.delete(candidate);
    }

    // Stages representing real downstream work (Round 2/3 evaluation, or a completed hire) — worth
    // protecting from accidental deletion. Round-1-only progress (pending/in-progress/submitted/
    // passed/failed) is exactly the transient written-test data this feature already treats as
    // disposable, so a batch stuck there (e.g. reported and done) is always safe to delete.
    private static final List<CandidateStage> PROTECTED_STAGES = List.of(
            CandidateStage.ROUND2_IN_PROGRESS, CandidateStage.ROUND2_SELECTED,
            CandidateStage.ROUND2_HOLD, CandidateStage.ROUND2_REJECTED,
            CandidateStage.ROUND3_IN_PROGRESS, CandidateStage.ROUND3_HOLD,
            CandidateStage.ROUND3_REJECTED, CandidateStage.CONVERTED);

    @Transactional
    public void deleteBatch(String batchId) {
        ScreeningBatch batch = getBatchOrThrow(batchId);
        long protectedCount = candidateRepository.countByBatchIdAndStageIn(batchId, PROTECTED_STAGES);
        if (protectedCount > 0) {
            throw new IllegalStateException("Cannot delete a batch with candidates who have progressed to Round 2/3 or been onboarded");
        }
        // screening_questions/screening_candidates/screening_answers cascade at the DB level (ON DELETE CASCADE).
        batchRepository.delete(batch);
    }

    private ScreeningBatch getBatchOrThrow(String batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new NoSuchElementException("Batch not found"));
    }
}
