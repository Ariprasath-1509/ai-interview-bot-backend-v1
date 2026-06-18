package com.benchreadiness.interview.service;

import com.benchreadiness.interview.client.AuthServiceClient;
import com.benchreadiness.interview.client.AiServiceClient;
import com.benchreadiness.interview.client.ComplianceServiceClient;
import com.benchreadiness.interview.dto.ClientBriefDto;
import com.benchreadiness.interview.entity.Engineer;
import com.benchreadiness.interview.entity.Interview;
import com.benchreadiness.interview.entity.JobDescription;
import com.benchreadiness.interview.repository.EngineerRepository;
import com.benchreadiness.interview.repository.InterviewRepository;
import com.benchreadiness.interview.repository.JobDescriptionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ClientBriefService {

    private static final Logger log = LoggerFactory.getLogger(ClientBriefService.class);

    private final InterviewRepository interviewRepository;
    private final EngineerRepository engineerRepository;
    private final JobDescriptionRepository jdRepository;
    private final ComplianceServiceClient complianceServiceClient;
    private final AuthServiceClient authServiceClient;
    private final AiServiceClient aiServiceClient;
    private final ObjectMapper objectMapper;

    public ClientBriefService(InterviewRepository interviewRepository,
                              EngineerRepository engineerRepository,
                              JobDescriptionRepository jdRepository,
                              ComplianceServiceClient complianceServiceClient,
                              AuthServiceClient authServiceClient,
                              AiServiceClient aiServiceClient,
                              ObjectMapper objectMapper) {
        this.interviewRepository = interviewRepository;
        this.engineerRepository = engineerRepository;
        this.jdRepository = jdRepository;
        this.complianceServiceClient = complianceServiceClient;
        this.authServiceClient = authServiceClient;
        this.aiServiceClient = aiServiceClient;
        this.objectMapper = objectMapper;
    }

    /** Returns saved client brief only — does not auto-load from assessment. */
    public ClientBriefResponse getClientBrief(String interviewId, String userId) {
        Interview interview = interviewRepository.findById(interviewId)
            .orElseThrow(() -> new IllegalArgumentException("Interview not found"));

        ClientBriefContext context = buildContext(interview);

        if (interview.getClientBriefJson() != null && !interview.getClientBriefJson().isBlank()) {
            try {
                Map<String, Object> stored = objectMapper.readValue(
                    interview.getClientBriefJson(), new TypeReference<>() {});
                ClientBriefDto brief = ClientBriefDto.fromMap(stored);
                brief.setSaved(true);
                return new ClientBriefResponse(brief, context, true);
            } catch (Exception e) {
                log.warn("Failed to parse saved client brief for {}: {}", interviewId, e.getMessage());
            }
        }

        return new ClientBriefResponse(null, context, false);
    }

    /** Generate a new AI client brief draft from the completed assessment (on demand). */
    public ClientBriefResponse generateClientBrief(String interviewId, String userId) {
        Interview interview = interviewRepository.findById(interviewId)
            .orElseThrow(() -> new IllegalArgumentException("Interview not found"));

        String assessmentJson = resolveAssessmentJson(interview, userId);
        if (assessmentJson == null || assessmentJson.isBlank()) {
            throw new IllegalArgumentException(
                "No AI assessment found. Run assessment before generating a client brief.");
        }

        JobDescription jd = jdRepository.findById(interview.getJdId()).orElse(null);
        String jdTitle = jd != null ? jd.getTitle() : "Position";
        String jdText = jd != null && jd.getText() != null ? jd.getText() : "";

        Map<String, Object> aiPayload = new LinkedHashMap<>();
        aiPayload.put("interviewId", interviewId);
        aiPayload.put("jdTitle", jdTitle);
        aiPayload.put("jdText", jdText);
        aiPayload.put("assessmentJson", assessmentJson);

        Map<String, Object> generated = aiServiceClient.generateClientBrief(aiPayload, userId);
        if (generated == null || generated.isEmpty()) {
            throw new IllegalArgumentException("AI returned an empty client brief");
        }
        if (generated.get("error") != null) {
            throw new IllegalArgumentException(String.valueOf(generated.get("error")));
        }

        ClientBriefDto brief = ClientBriefDto.fromMap(generated);
        brief.setSaved(false);
        brief.setSource(brief.getSource() != null && !brief.getSource().isBlank() ? brief.getSource() : "ai");
        return new ClientBriefResponse(brief, buildContext(interview), false);
    }

    @Transactional
    public ClientBriefResponse saveClientBrief(String interviewId, ClientBriefDto brief,
                                               String userId, String userName) {
        Interview interview = interviewRepository.findById(interviewId)
            .orElseThrow(() -> new IllegalArgumentException("Interview not found"));

        try {
            Map<String, Object> payload = toStorageMap(brief, userId, userName);
            interview.setClientBriefJson(objectMapper.writeValueAsString(payload));
            interviewRepository.save(interview);

            ClientBriefDto saved = ClientBriefDto.fromMap(payload);
            saved.setSaved(true);
            saved.setLastEditedByUserId(userId);
            saved.setLastEditedByName(userName);
            saved.setLastEditedAt(Instant.now());
            saved.setSource("manual");
            return new ClientBriefResponse(saved, buildContext(interview), true);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to save client brief: " + e.getMessage());
        }
    }

    public ClientBriefPdfContext getPdfContext(String interviewId, String userId) {
        ClientBriefResponse response = getClientBrief(interviewId, userId);
        if (response.brief() == null || !response.brief().isSaved()) {
            throw new IllegalArgumentException(
                "Save the client brief before downloading. Review and edit the draft, then click Save.");
        }
        return new ClientBriefPdfContext(response.brief(), response.context());
    }

    private String resolveAssessmentJson(Interview interview, String userId) {
        String json = interview.getAssessmentResultJson();
        if (json != null && !json.isBlank()) {
            return json;
        }
        try {
            Map<String, Object> compliance = complianceServiceClient.getAssessmentResponse(
                interview.getId(), userId);
            if (compliance != null && compliance.get("assessmentJson") != null) {
                return compliance.get("assessmentJson").toString();
            }
        } catch (Exception e) {
            log.warn("Compliance assessment fetch failed for {}: {}", interview.getId(), e.getMessage());
        }
        return extractAssessmentFromTranscript(interview.getTranscriptJson());
    }

    @SuppressWarnings("unchecked")
    private String extractAssessmentFromTranscript(String transcriptJson) {
        if (transcriptJson == null || transcriptJson.isBlank()) return null;
        try {
            Map<String, Object> doc = objectMapper.readValue(transcriptJson, Map.class);
            Object meta = doc.get("meta");
            if (meta instanceof Map<?, ?> metaMap) {
                Object assessment = metaMap.get("assessment");
                if (assessment != null) {
                    return assessment instanceof String s ? s : objectMapper.writeValueAsString(assessment);
                }
            }
        } catch (Exception e) {
            log.debug("No assessment in transcript for interview: {}", e.getMessage());
        }
        return null;
    }

    private Map<String, Object> toStorageMap(ClientBriefDto brief, String userId, String userName) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("executiveSummary", brief.getExecutiveSummary());
        map.put("keyStrengths", brief.getKeyStrengths());
        map.put("areasToNote", brief.getAreasToNote());
        map.put("recommendation", brief.getRecommendation());
        map.put("recommendedFor", brief.getRecommendedFor());
        map.put("suggestedNextStep", brief.getSuggestedNextStep());
        map.put("source", "manual");
        map.put("lastEditedByUserId", userId);
        map.put("lastEditedByName", userName);
        map.put("lastEditedAt", Instant.now().toString());

        Map<String, Object> fit = new LinkedHashMap<>();
        fit.put("overall", brief.getTechnicalFit().getOverall());
        fit.put("highlights", brief.getTechnicalFit().getHighlights());
        fit.put("gaps", brief.getTechnicalFit().getGaps());
        map.put("technicalFit", fit);

        Map<String, Object> perf = new LinkedHashMap<>();
        perf.put("communication", brief.getInterviewPerformance().getCommunication());
        perf.put("problemSolving", brief.getInterviewPerformance().getProblemSolving());
        perf.put("overallRating", brief.getInterviewPerformance().getOverallRating());
        map.put("interviewPerformance", perf);
        return map;
    }

    private ClientBriefContext buildContext(Interview interview) {
        Engineer engineer = engineerRepository.findById(interview.getEngineerId()).orElse(null);
        JobDescription jd = jdRepository.findById(interview.getJdId()).orElse(null);

        String candidateName = engineer != null && engineer.getName() != null ? engineer.getName() : "Candidate";
        String candidateEmail = engineer != null ? engineer.getEmail() : "";
        String jdTitle = jd != null ? jd.getTitle() : "Position";
        String interviewDate = interview.getCreatedAt() != null ? interview.getCreatedAt().toString() : "";

        Double yoeActual = null;
        Double yoePortrayed = null;
        String skillSet = null;
        if (engineer != null && engineer.getUserId() != null) {
            try {
                Map<String, Object> user = authServiceClient.getUserById(engineer.getUserId());
                if (user.get("yoeActual") != null) {
                    yoeActual = Double.valueOf(user.get("yoeActual").toString());
                }
                if (user.get("yoePortrayed") != null) {
                    yoePortrayed = Double.valueOf(user.get("yoePortrayed").toString());
                }
                skillSet = user.get("skillSet") != null ? user.get("skillSet").toString() : null;
            } catch (Exception ignored) {
                // optional enrichment
            }
        }

        return new ClientBriefContext(
            candidateName,
            candidateEmail,
            jdTitle,
            interviewDate,
            interview.getInterviewMode() != null ? interview.getInterviewMode().name() : "SCREENING",
            interview.getFinalVerdict() != null ? interview.getFinalVerdict().name()
                : interview.getProposedVerdict() != null ? interview.getProposedVerdict().name() : null,
            skillSet,
            yoeActual,
            yoePortrayed
        );
    }

    public record ClientBriefResponse(ClientBriefDto brief, ClientBriefContext context, boolean hasSavedBrief) {}

    public record ClientBriefContext(
        String candidateName,
        String candidateEmail,
        String jdTitle,
        String interviewDate,
        String interviewMode,
        String verdict,
        String skillSet,
        Double yoeActual,
        Double yoePortrayed
    ) {}

    public record ClientBriefPdfContext(ClientBriefDto brief, ClientBriefContext context) {}
}
