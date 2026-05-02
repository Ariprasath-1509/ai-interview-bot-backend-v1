package com.benchreadiness.review.service;

import com.benchreadiness.review.client.ComplianceServiceClient;
import com.benchreadiness.review.client.InterviewServiceClient;
import com.benchreadiness.review.dto.SaveScoresRequest;
import com.benchreadiness.review.dto.SignOffRequest;
import com.benchreadiness.review.entity.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReviewService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ReviewService.class);

    private final ScoreRepository scoreRepository;
    private final SignOffRepository signOffRepository;
    private final InterviewServiceClient interviewServiceClient;
    private final ComplianceServiceClient complianceServiceClient;

    public ReviewService(ScoreRepository scoreRepository, SignOffRepository signOffRepository, 
                        InterviewServiceClient interviewServiceClient,
                        ComplianceServiceClient complianceServiceClient) {
        this.scoreRepository = scoreRepository;
        this.signOffRepository = signOffRepository;
        this.interviewServiceClient = interviewServiceClient;
        this.complianceServiceClient = complianceServiceClient;
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
        List<Score> saved = scoreRepository.saveAll(scores);
        
        // Log audit trail
        logAudit("system", "System", "SYSTEM", "SCORES_SAVED", req.getInterviewId(),
            String.format("Saved %d category scores", scores.size()), null, null);
        
        return saved;
    }

    @Transactional
    public SignOff signOff(SignOffRequest req, String reviewerUserId) {
        log.info("Sign-off requested for interview {} by user {} with verdict {}", 
            req.getInterviewId(), reviewerUserId, req.getVerdict());
        
        SignOff signOff = signOffRepository.findByInterviewId(req.getInterviewId())
                .orElseGet(SignOff::new);

        String oldVerdict = signOff.getFinalVerdict() != null ? signOff.getFinalVerdict().name() : null;
        
        signOff.setInterviewId(req.getInterviewId());
        signOff.setReviewerUserId(reviewerUserId);
        signOff.setFinalVerdict(req.getVerdict());
        signOff.setNote(req.getNote());
        SignOff saved = signOffRepository.save(signOff);
        
        log.info("Sign-off saved for interview {}, now updating interview status", req.getInterviewId());

        // Update interview status to SIGNED_OFF in interview-service
        try {
            Map<String, Object> updates = Map.of(
                "status", "SIGNED_OFF", 
                "finalVerdict", req.getVerdict().name()
            );
            log.info("Calling interview-service to update interview {} with: {}", req.getInterviewId(), updates);
            interviewServiceClient.updateInterview(req.getInterviewId(), updates);
            log.info("Successfully updated interview {} status to SIGNED_OFF", req.getInterviewId());
        } catch (Exception e) {
            log.error("Failed to update interview status to SIGNED_OFF for {}: {}", 
                req.getInterviewId(), e.getMessage(), e);
        }

        // Log audit trail
        String action = oldVerdict == null ? "INTERVIEW_SIGNED_OFF" : "SIGN_OFF_UPDATED";
        logAudit(reviewerUserId, null, "ADMIN", action, req.getInterviewId(),
            String.format("Verdict: %s, Note: %s", req.getVerdict(), req.getNote() != null ? req.getNote() : "N/A"),
            oldVerdict, req.getVerdict().name());

        return saved;
    }

    public SignOff getSignOff(String interviewId) {
        return signOffRepository.findByInterviewId(interviewId).orElse(null);
    }

    private void logAudit(String actorId, String actorName, String actorRole, String action, 
                         String resourceId, String detail, String oldValue, String newValue) {
        try {
            Map<String, Object> auditLog = new HashMap<>();
            auditLog.put("actorId", actorId);
            if (actorName != null) auditLog.put("actorName", actorName);
            auditLog.put("actorRole", actorRole);
            auditLog.put("action", action);
            auditLog.put("resource", "REVIEW");
            auditLog.put("resourceId", resourceId);
            if (detail != null) auditLog.put("detail", detail);
            if (oldValue != null) auditLog.put("oldValue", oldValue);
            if (newValue != null) auditLog.put("newValue", newValue);
            auditLog.put("ipAddress", "system");
            complianceServiceClient.recordAuditLog(auditLog);
        } catch (Exception e) {
            log.error("Failed to record audit log: {}", e.getMessage());
        }
    }
}
