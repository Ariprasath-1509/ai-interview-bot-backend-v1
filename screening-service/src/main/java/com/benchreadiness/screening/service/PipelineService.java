package com.benchreadiness.screening.service;

import com.benchreadiness.screening.client.AuthServiceClient;
import com.benchreadiness.screening.dto.RoundFeedbackRequest;
import com.benchreadiness.screening.entity.ScreeningAnswer;
import com.benchreadiness.screening.entity.ScreeningCandidate;
import com.benchreadiness.screening.entity.enums.CandidateStage;
import com.benchreadiness.screening.entity.enums.RoundDecision;
import com.benchreadiness.screening.repository.ScreeningAnswerRepository;
import com.benchreadiness.screening.repository.ScreeningCandidateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class PipelineService {

    private final ScreeningCandidateRepository candidateRepository;
    private final ScreeningAnswerRepository answerRepository;
    private final AuthServiceClient authServiceClient;

    public PipelineService(ScreeningCandidateRepository candidateRepository,
                           ScreeningAnswerRepository answerRepository,
                           AuthServiceClient authServiceClient) {
        this.candidateRepository = candidateRepository;
        this.answerRepository = answerRepository;
        this.authServiceClient = authServiceClient;
    }

    public List<ScreeningCandidate> round1Results(String batchId) {
        return candidateRepository.findByBatchId(batchId);
    }

    public List<ScreeningAnswer> candidateAnswers(String candidateId) {
        return answerRepository.findByCandidateIdOrderByQuestionDisplayIndexAsc(candidateId);
    }

    @Transactional
    public ScreeningCandidate markRound1(String candidateId, boolean passed) {
        ScreeningCandidate candidate = getOrThrow(candidateId);
        if (candidate.getStage() != CandidateStage.ROUND1_SUBMITTED) {
            throw new IllegalStateException("Candidate has not submitted Round 1 yet");
        }
        candidate.setStage(passed ? CandidateStage.ROUND1_PASSED : CandidateStage.ROUND1_FAILED);
        return candidateRepository.save(candidate);
    }

    @Transactional
    public ScreeningCandidate setAllowLateSubmission(String candidateId, boolean allow) {
        ScreeningCandidate candidate = getOrThrow(candidateId);
        if (candidate.getStage() != CandidateStage.ROUND1_PENDING && candidate.getStage() != CandidateStage.ROUND1_IN_PROGRESS) {
            throw new IllegalStateException("Candidate has already submitted Round 1 — nothing to accept");
        }
        candidate.setAllowLateSubmission(allow);
        return candidateRepository.save(candidate);
    }

    public List<ScreeningCandidate> round2Queue() {
        List<ScreeningCandidate> queue = new java.util.ArrayList<>(candidateRepository.findByStage(CandidateStage.ROUND1_PASSED));
        queue.addAll(candidateRepository.findByStage(CandidateStage.ROUND2_IN_PROGRESS));
        return queue;
    }

    @Transactional
    public ScreeningCandidate startRound2(String candidateId) {
        ScreeningCandidate candidate = getOrThrow(candidateId);
        if (candidate.getStage() != CandidateStage.ROUND1_PASSED) {
            throw new IllegalStateException("Candidate is not eligible for Round 2");
        }
        candidate.setStage(CandidateStage.ROUND2_IN_PROGRESS);
        candidate.setRound2StartedAt(Instant.now());
        return candidateRepository.save(candidate);
    }

    @Transactional
    public ScreeningCandidate submitRound2Feedback(String candidateId, RoundFeedbackRequest req, String recordedByUserId) {
        ScreeningCandidate candidate = getOrThrow(candidateId);
        if (candidate.getStage() != CandidateStage.ROUND2_IN_PROGRESS) {
            throw new IllegalStateException("Round 2 has not been started for this candidate");
        }
        candidate.setRound2Strengths(req.getStrengths());
        candidate.setRound2Weaknesses(req.getWeaknesses());
        candidate.setRound2Practical(req.getPractical());
        candidate.setRound2Improvements(req.getImprovements());
        candidate.setRound2Result(req.getResult());
        candidate.setRound2RecordedBy(recordedByUserId);
        candidate.setRound2RecordedAt(Instant.now());
        candidate.setStage(switch (req.getResult()) {
            case SELECTED -> CandidateStage.ROUND2_SELECTED;
            case HOLD -> CandidateStage.ROUND2_HOLD;
            case REJECTED -> CandidateStage.ROUND2_REJECTED;
        });
        return candidateRepository.save(candidate);
    }

    public List<ScreeningCandidate> round3Queue() {
        List<ScreeningCandidate> queue = new java.util.ArrayList<>(candidateRepository.findByStage(CandidateStage.ROUND2_SELECTED));
        queue.addAll(candidateRepository.findByStage(CandidateStage.ROUND3_IN_PROGRESS));
        return queue;
    }

    @Transactional
    public ScreeningCandidate startRound3(String candidateId) {
        ScreeningCandidate candidate = getOrThrow(candidateId);
        if (candidate.getStage() != CandidateStage.ROUND2_SELECTED) {
            throw new IllegalStateException("Candidate is not eligible for Round 3");
        }
        candidate.setStage(CandidateStage.ROUND3_IN_PROGRESS);
        candidate.setRound3StartedAt(Instant.now());
        return candidateRepository.save(candidate);
    }

    @Transactional
    public ScreeningCandidate submitRound3Feedback(String candidateId, RoundFeedbackRequest req,
                                                   String recordedByUserId, String recordedByRole) throws Exception {
        ScreeningCandidate candidate = getOrThrow(candidateId);
        if (candidate.getStage() != CandidateStage.ROUND3_IN_PROGRESS) {
            throw new IllegalStateException("Round 3 has not been started for this candidate");
        }
        candidate.setRound3Strengths(req.getStrengths());
        candidate.setRound3Weaknesses(req.getWeaknesses());
        candidate.setRound3Practical(req.getPractical());
        candidate.setRound3Improvements(req.getImprovements());
        candidate.setRound3Result(req.getResult());
        candidate.setRound3RecordedBy(recordedByUserId);
        candidate.setRound3RecordedAt(Instant.now());

        if (req.getResult() == RoundDecision.SELECTED) {
            RoundFeedbackRequest.ConversionDetails details = req.getConversionDetails();
            boolean hasContactNumber = candidate.getContactNumber() != null || (details != null && isNotBlank(details.getContactNumber()));
            boolean hasSource = candidate.getInstitute() != null || (details != null && isNotBlank(details.getSource()));
            boolean hasBatchAndSkill = details != null && isNotBlank(details.getBatchLabel()) && isNotBlank(details.getSkillSet());
            if (!hasContactNumber || !hasSource || !hasBatchAndSkill) {
                throw new IllegalArgumentException("Contact number, training batch, source, and skill set are required to onboard a passed candidate");
            }
            String createdUserId = convertToPermanentCandidate(candidate, details, recordedByUserId, recordedByRole);
            candidate.setConvertedUserId(createdUserId);
            candidate.setStage(CandidateStage.CONVERTED);
        } else if (req.getResult() == RoundDecision.HOLD) {
            candidate.setStage(CandidateStage.ROUND3_HOLD);
        } else {
            candidate.setStage(CandidateStage.ROUND3_REJECTED);
        }
        return candidateRepository.save(candidate);
    }

    @SuppressWarnings("unchecked")
    private String convertToPermanentCandidate(ScreeningCandidate candidate, RoundFeedbackRequest.ConversionDetails details,
                                               String callerId, String callerRole) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", candidate.getName());
        request.put("officialEmail", candidate.getEmail());
        // Prefer what was already collected at CSV intake; the Round 3 form only fills gaps.
        request.put("contactNumber", firstNonBlank(candidate.getContactNumber(), details.getContactNumber()));
        request.put("batch", details.getBatchLabel());
        request.put("source", firstNonBlank(candidate.getInstitute(), details.getSource()));
        request.put("skillSet", details.getSkillSet());
        if (candidate.getYop() != null) {
            request.put("yop", candidate.getYop());
        }
        if (candidate.getExperience() != null) {
            request.put("yoeActual", candidate.getExperience());
        }
        request.put("candidateStatus", "Under Training");

        Map<String, Object> response = authServiceClient.createCandidate(request, callerId, callerRole);
        Object candidateId = response.get("candidateId");
        return candidateId != null ? candidateId.toString() : null;
    }

    private ScreeningCandidate getOrThrow(String candidateId) {
        return candidateRepository.findById(candidateId)
                .orElseThrow(() -> new NoSuchElementException("Candidate not found"));
    }

    private boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (isNotBlank(v)) return v;
        }
        return null;
    }
}
