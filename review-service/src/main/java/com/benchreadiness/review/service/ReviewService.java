package com.benchreadiness.review.service;

import com.benchreadiness.review.client.InterviewServiceClient;
import com.benchreadiness.review.dto.SaveScoresRequest;
import com.benchreadiness.review.dto.SignOffRequest;
import com.benchreadiness.review.entity.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ReviewService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ReviewService.class);

    private final ScoreRepository scoreRepository;
    private final SignOffRepository signOffRepository;
    private final InterviewServiceClient interviewServiceClient;

    public ReviewService(ScoreRepository scoreRepository, SignOffRepository signOffRepository, InterviewServiceClient interviewServiceClient) {
        this.scoreRepository = scoreRepository;
        this.signOffRepository = signOffRepository;
        this.interviewServiceClient = interviewServiceClient;
    }

    public List<Score> getScores(String interviewId) {
        return scoreRepository.findByInterviewId(interviewId);
    }

    @Transactional
    public List<Score> saveScores(SaveScoresRequest req) {
        scoreRepository.deleteByInterviewId(req.getInterviewId());
        List<Score> scores = req.getScores().stream().map(item -> {
            Score s = new Score();
            s.setInterviewId(req.getInterviewId());
            s.setDimension(item.dimension());
            s.setValue(item.value());
            s.setRationale(item.rationale());
            s.setEvidence(item.evidence());
            s.setGap(item.gap());
            s.setStrengths(item.strengths());
            s.setWeaknesses(item.weaknesses());
            s.setConfidence(item.confidence());
            return s;
        }).toList();
        return scoreRepository.saveAll(scores);
    }

    @Transactional
    public SignOff signOff(SignOffRequest req, String reviewerUserId) {
        SignOff signOff = signOffRepository.findByInterviewId(req.getInterviewId())
                .orElseGet(SignOff::new);

        signOff.setInterviewId(req.getInterviewId());
        signOff.setReviewerUserId(reviewerUserId);
        signOff.setFinalVerdict(req.getVerdict());
        signOff.setNote(req.getNote());
        SignOff saved = signOffRepository.save(signOff);

        // Update interview status to SIGNED_OFF in interview-service
        try {
            interviewServiceClient.updateInterview(req.getInterviewId(), 
                Map.of("status", "SIGNED_OFF", "finalVerdict", req.getVerdict().name()));
        } catch (Exception e) {
            log.warn("Failed to update interview status to SIGNED_OFF for {}: {}", req.getInterviewId(), e.getMessage());
        }

        return saved;
    }

    public SignOff getSignOff(String interviewId) {
        return signOffRepository.findByInterviewId(interviewId).orElse(null);
    }
}
