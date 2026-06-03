package com.benchreadiness.ai.service;

import com.benchreadiness.ai.client.ComplianceServiceClient;
import com.benchreadiness.ai.client.InterviewServiceClient;
import com.benchreadiness.ai.dto.AssessmentRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AsyncAssessmentService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AsyncAssessmentService.class);

    private final AssessmentService assessmentService;
    private final ComplianceServiceClient complianceServiceClient;
    private final InterviewServiceClient interviewServiceClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, AssessmentStatus> assessmentCache = new ConcurrentHashMap<>();

    public AsyncAssessmentService(AssessmentService assessmentService,
                                 ComplianceServiceClient complianceServiceClient,
                                 InterviewServiceClient interviewServiceClient) {
        this.assessmentService = assessmentService;
        this.complianceServiceClient = complianceServiceClient;
        this.interviewServiceClient = interviewServiceClient;
    }

    public void markProcessing(String interviewId) {
        assessmentCache.put(interviewId, new AssessmentStatus("PROCESSING", null, null));
        persistStatus(interviewId, "PROCESSING", null, null);
    }

    @Async("assessmentExecutor")
    public void processAssessmentAsync(AssessmentRequest req, String userId) {
        String interviewId = req.getInterviewId();
        log.info("Starting async assessment for interview: {}", interviewId);

        try {
            assessmentCache.put(interviewId, new AssessmentStatus("PROCESSING", null, null));
            persistStatus(interviewId, "PROCESSING", null, null);

            Map<String, Object> result = assessmentService.assess(req, userId);

            String resultJson = objectMapper.writeValueAsString(result);
            assessmentCache.put(interviewId, new AssessmentStatus("COMPLETED", resultJson, null));
            persistStatus(interviewId, "COMPLETED", null, resultJson);

            log.info("Async assessment completed for interview: {}", interviewId);
        } catch (Exception e) {
            log.error("Async assessment failed for interview {}: {}", interviewId, e.getMessage(), e);
            assessmentCache.put(interviewId, new AssessmentStatus("FAILED", null, e.getMessage()));
            persistStatus(interviewId, "FAILED", e.getMessage(), null);
        }
    }

    public AssessmentStatus getAssessmentStatus(String interviewId) {
        AssessmentStatus cached = assessmentCache.get(interviewId);
        if (cached != null && !"NOT_FOUND".equals(cached.getStatus())) {
            return cached;
        }
        return loadFromInterview(interviewId);
    }

    public void clearAssessmentStatus(String interviewId) {
        assessmentCache.remove(interviewId);
    }

    private AssessmentStatus loadFromInterview(String interviewId) {
        try {
            Map<String, Object> interview = interviewServiceClient.getInterview(interviewId);
            String status = interview.get("assessmentStatus") != null
                    ? String.valueOf(interview.get("assessmentStatus")) : null;
            if (status == null || status.isBlank()) {
                return new AssessmentStatus("NOT_FOUND", null, null);
            }
            String resultJson = interview.get("assessmentResultJson") != null
                    ? String.valueOf(interview.get("assessmentResultJson")) : null;
            String error = interview.get("assessmentError") != null
                    ? String.valueOf(interview.get("assessmentError")) : null;
            return new AssessmentStatus(status, resultJson, error);
        } catch (Exception e) {
            log.debug("Could not load assessment status from interview {}: {}", interviewId, e.getMessage());
            return new AssessmentStatus("NOT_FOUND", null, null);
        }
    }

    private void persistStatus(String interviewId, String status, String error, String resultJson) {
        try {
            java.util.HashMap<String, Object> body = new java.util.HashMap<>();
            body.put("status", status);
            if (error != null) body.put("error", error);
            if (resultJson != null) body.put("resultJson", resultJson);
            interviewServiceClient.updateAssessmentStatus(interviewId, body);
        } catch (Exception e) {
            log.warn("Failed to persist assessment status for {}: {}", interviewId, e.getMessage());
        }
    }

    public static class AssessmentStatus {
        private final String status;
        private final String result;
        private final String error;

        public AssessmentStatus(String status, String result, String error) {
            this.status = status;
            this.result = result;
            this.error = error;
        }

        public String getStatus() { return status; }
        public String getResult() { return result; }
        public String getError() { return error; }
    }
}
