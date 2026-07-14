package com.benchreadiness.interview.service;

import com.benchreadiness.interview.client.AuthServiceClient;
import com.benchreadiness.interview.client.ComplianceServiceClient;
import com.benchreadiness.interview.dto.CandidateReviewSummary;
import com.benchreadiness.interview.entity.Interview;
import com.benchreadiness.interview.entity.ReadinessVerdict;
import com.benchreadiness.interview.repository.EngineerRepository;
import com.benchreadiness.interview.repository.InterviewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CandidateReviewService {

    private static final Logger log = LoggerFactory.getLogger(CandidateReviewService.class);
    private final InterviewRepository interviewRepository;
    private final EngineerRepository engineerRepository;
    private final AuthServiceClient authServiceClient;
    private final ComplianceServiceClient complianceServiceClient;
    private final ObjectMapper objectMapper;

    public CandidateReviewService(InterviewRepository interviewRepository,
                                 EngineerRepository engineerRepository,
                                 AuthServiceClient authServiceClient,
                                 ComplianceServiceClient complianceServiceClient,
                                 ObjectMapper objectMapper) {
        this.interviewRepository = interviewRepository;
        this.engineerRepository = engineerRepository;
        this.authServiceClient = authServiceClient;
        this.complianceServiceClient = complianceServiceClient;
        this.objectMapper = objectMapper;
    }

    public CandidateReviewSummary getCandidateReviewSummary(String candidateId, String userId) {
        Map<String, Object> candidateData = authServiceClient.getCandidateById(candidateId, userId);
        String candidateEmail = (String) candidateData.get("email");
        
        log.info("Fetching interviews for candidate email: {}", candidateEmail);
        
        // Find engineer by email to get engineer ID
        List<Interview> allInterviews = interviewRepository.findByCandidateEmailOrderByCreatedAtDesc(candidateEmail);
        
        log.info("Found {} total interviews for candidate", allInterviews.size());
        
        List<Interview> completedInterviews = allInterviews.stream()
                .filter(i -> i.getStatus().name().equals("COMPLETED") || i.getStatus().name().equals("SIGNED_OFF"))
                .limit(5)
                .collect(Collectors.toList());
        
        log.info("Found {} completed/signed-off interviews", completedInterviews.size());

        CandidateReviewSummary summary = new CandidateReviewSummary();
        
        summary.setCandidateInfo(buildCandidateInfo(candidateData));
        summary.setOverviewStats(buildOverviewStats(completedInterviews));
        summary.setInterviews(buildInterviewDetails(completedInterviews, userId));

        return summary;
    }

    private CandidateReviewSummary.CandidateInfo buildCandidateInfo(Map<String, Object> candidateData) {
        return new CandidateReviewSummary.CandidateInfo(
                (String) candidateData.get("name"),
                (String) candidateData.get("email"),
                (String) candidateData.get("skillSet"),
                getDoubleValue(candidateData.get("yoeActual")),
                getDoubleValue(candidateData.get("yoePortrayed")),
                (String) candidateData.get("batch"),
                (String) candidateData.get("source")
        );
    }

    private CandidateReviewSummary.OverviewStats buildOverviewStats(List<Interview> interviews) {
        int total = interviews.size();
        long readyCount = interviews.stream()
                .filter(i -> i.getFinalVerdict() == ReadinessVerdict.READY || 
                           (i.getFinalVerdict() == null && i.getProposedVerdict() == ReadinessVerdict.READY))
                .count();
        double successRate = total > 0 ? (readyCount * 100.0 / total) : 0.0;

        return new CandidateReviewSummary.OverviewStats(total, (int) readyCount, successRate);
    }

    private List<CandidateReviewSummary.InterviewDetail> buildInterviewDetails(List<Interview> interviews, String userId) {
        return interviews.stream()
                .map(interview -> buildInterviewDetail(interview, userId))
                .collect(Collectors.toList());
    }

    private CandidateReviewSummary.InterviewDetail buildInterviewDetail(Interview interview, String userId) {
        CandidateReviewSummary.InterviewDetail detail = new CandidateReviewSummary.InterviewDetail();
        detail.setInterviewId(interview.getId());
        detail.setInterviewDate(interview.getCreatedAt());
        detail.setJdTitle(getJdTitle(interview));
        detail.setInterviewMode(interview.getInterviewMode().name());
        
        String verdict = interview.getFinalVerdict() != null ? 
                interview.getFinalVerdict().name() : 
                (interview.getProposedVerdict() != null ? interview.getProposedVerdict().name() : "PENDING");
        detail.setVerdict(verdict);

        try {
            Map<String, Object> assessmentData = complianceServiceClient.getAssessmentResponse(interview.getId(), userId);
            if (assessmentData != null && assessmentData.containsKey("assessmentJson")) {
                String assessmentJson = (String) assessmentData.get("assessmentJson");
                Map<String, Object> assessment = objectMapper.readValue(assessmentJson, Map.class);
                
                detail.setCategoryScores(extractCategoryScores(assessment));
                detail.setProsAndCons(extractProsAndCons(assessment));
                detail.setResumeConsistency(extractResumeConsistency(assessment));
                detail.setBehavioralSignals(extractBehavioralSignals(assessment));
                detail.setRoadmap(extractRoadmap(assessment));

                // Onboarding assessments are a flat {score, summary, strengths[], gaps[]} shape
                // (see AssessmentService.onboardingAssessment) — none of the extractors above find
                // anything in it, so fall back to a synthetic single-row rendering.
                if (detail.getCategoryScores().isEmpty() && assessment.get("score") instanceof Number) {
                    detail.setCategoryScores(buildOnboardingCategoryScore(assessment));
                    detail.setProsAndCons(buildOnboardingProsAndCons(assessment));
                    detail.setSummary((String) assessment.get("summary"));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch assessment for interview {}: {}", interview.getId(), e.getMessage());
        }

        return detail;
    }

    private String getJdTitle(Interview interview) {
        try {
            return "Position " + interview.getJdId().substring(0, 8);
        } catch (Exception e) {
            return "Interview Position";
        }
    }

    private List<CandidateReviewSummary.CategoryScore> extractCategoryScores(Map<String, Object> assessment) {
        List<CandidateReviewSummary.CategoryScore> scores = new ArrayList<>();
        Object categoryScoresObj = assessment.get("categoryScores");
        
        if (categoryScoresObj instanceof List) {
            List<Map<String, Object>> categoryScores = (List<Map<String, Object>>) categoryScoresObj;
            for (Map<String, Object> scoreData : categoryScores) {
                CandidateReviewSummary.CategoryScore score = new CandidateReviewSummary.CategoryScore();
                score.setDimension((String) scoreData.get("dimension"));
                score.setValue(getIntValue(scoreData.get("value")));
                score.setStrengths((String) scoreData.get("strengths"));
                score.setWeaknesses((String) scoreData.get("weaknesses"));
                score.setEvidence((String) scoreData.get("evidence"));
                score.setGap((String) scoreData.get("gap"));
                score.setConfidence((String) scoreData.get("confidence"));
                scores.add(score);
            }
        }
        return scores;
    }

    @SuppressWarnings("unchecked")
    private List<CandidateReviewSummary.CategoryScore> buildOnboardingCategoryScore(Map<String, Object> assessment) {
        CandidateReviewSummary.CategoryScore score = new CandidateReviewSummary.CategoryScore();
        score.setDimension("ConceptUnderstanding");
        score.setValue(getIntValue(assessment.get("score")));
        List<String> gaps = (List<String>) assessment.get("gaps");
        score.setGap(gaps != null && !gaps.isEmpty() ? String.join("; ", gaps) : null);
        List<String> strengths = (List<String>) assessment.get("strengths");
        score.setStrengths(strengths != null && !strengths.isEmpty() ? String.join("; ", strengths) : null);
        return List.of(score);
    }

    @SuppressWarnings("unchecked")
    private List<CandidateReviewSummary.ProCon> buildOnboardingProsAndCons(Map<String, Object> assessment) {
        CandidateReviewSummary.ProCon pc = new CandidateReviewSummary.ProCon();
        pc.setCategory("Concept Understanding");
        pc.setPros((List<String>) assessment.get("strengths"));
        pc.setCons((List<String>) assessment.get("gaps"));
        return List.of(pc);
    }

    private List<CandidateReviewSummary.ProCon> extractProsAndCons(Map<String, Object> assessment) {
        List<CandidateReviewSummary.ProCon> prosAndCons = new ArrayList<>();
        Object feedbackObj = assessment.get("candidateFeedback");
        
        if (feedbackObj instanceof Map) {
            Map<String, Object> feedback = (Map<String, Object>) feedbackObj;
            Object prosAndConsObj = feedback.get("prosAndCons");
            
            if (prosAndConsObj instanceof List) {
                List<Map<String, Object>> prosAndConsList = (List<Map<String, Object>>) prosAndConsObj;
                for (Map<String, Object> pcData : prosAndConsList) {
                    CandidateReviewSummary.ProCon pc = new CandidateReviewSummary.ProCon();
                    pc.setCategory((String) pcData.get("category"));
                    pc.setPros((List<String>) pcData.get("pros"));
                    pc.setCons((List<String>) pcData.get("cons"));
                    prosAndCons.add(pc);
                }
            }
        }
        return prosAndCons;
    }

    private CandidateReviewSummary.ResumeConsistency extractResumeConsistency(Map<String, Object> assessment) {
        Object consistencyObj = assessment.get("resumeConsistency");
        if (consistencyObj instanceof Map) {
            Map<String, Object> data = (Map<String, Object>) consistencyObj;
            CandidateReviewSummary.ResumeConsistency consistency = new CandidateReviewSummary.ResumeConsistency();
            consistency.setClaimed((List<String>) data.get("claimed"));
            consistency.setDemonstrated((List<String>) data.get("demonstrated"));
            consistency.setNotDemonstrated((List<String>) data.get("notDemonstrated"));
            consistency.setConsistencyScore(getIntValue(data.get("consistencyScore")));
            consistency.setFlags((List<String>) data.get("flags"));
            return consistency;
        }
        return null;
    }

    private CandidateReviewSummary.BehavioralSignals extractBehavioralSignals(Map<String, Object> assessment) {
        Object signalsObj = assessment.get("behavioralSignals");
        if (signalsObj instanceof Map) {
            Map<String, Object> data = (Map<String, Object>) signalsObj;
            CandidateReviewSummary.BehavioralSignals signals = new CandidateReviewSummary.BehavioralSignals();
            signals.setOwnershipLevel((String) data.get("ownershipLevel"));
            signals.setLearningAgility((String) data.get("learningAgility"));
            signals.setCommunicationStructure((String) data.get("communicationStructure"));
            signals.setConfidenceCalibration((String) data.get("confidenceCalibration"));
            signals.setSummary((String) data.get("summary"));
            return signals;
        }
        return null;
    }

    private List<CandidateReviewSummary.RoadmapItem> extractRoadmap(Map<String, Object> assessment) {
        List<CandidateReviewSummary.RoadmapItem> roadmap = new ArrayList<>();
        Object feedbackObj = assessment.get("candidateFeedback");
        
        if (feedbackObj instanceof Map) {
            Map<String, Object> feedback = (Map<String, Object>) feedbackObj;
            Object roadmapObj = feedback.get("roadmap");
            
            if (roadmapObj instanceof List) {
                List<Map<String, Object>> roadmapList = (List<Map<String, Object>>) roadmapObj;
                for (Map<String, Object> itemData : roadmapList) {
                    CandidateReviewSummary.RoadmapItem item = new CandidateReviewSummary.RoadmapItem();
                    item.setDay((String) itemData.get("day"));
                    item.setCategory((String) itemData.get("category"));
                    item.setGap((String) itemData.get("gap"));
                    item.setFocus((String) itemData.get("focus"));
                    item.setWhyItMatters((String) itemData.get("whyItMatters"));
                    item.setResource((String) itemData.get("resource"));
                    item.setExercise((String) itemData.get("exercise"));
                    item.setEstimatedHours(getIntValue(itemData.get("estimatedHours")));
                    roadmap.add(item);
                }
            }
        }
        return roadmap;
    }

    private Double getDoubleValue(Object value) {
        if (value == null) return 0.0;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private int getIntValue(Object value) {
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return 0;
        }
    }
}
