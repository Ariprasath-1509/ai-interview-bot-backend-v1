package com.benchreadiness.ai.service;

import com.benchreadiness.ai.client.ComplianceServiceClient;
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
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // In-memory cache for assessment status and results
    private final Map<String, AssessmentStatus> assessmentCache = new ConcurrentHashMap<>();

    public AsyncAssessmentService(AssessmentService assessmentService, 
                                 ComplianceServiceClient complianceServiceClient) {
        this.assessmentService = assessmentService;
        this.complianceServiceClient = complianceServiceClient;
    }

    @Async("assessmentExecutor")
    public void processAssessmentAsync(AssessmentRequest req, String userId) {
        String interviewId = req.getInterviewId();
        log.info("Starting async assessment for interview: {}", interviewId);
        
        try {
            // Mark as processing
            assessmentCache.put(interviewId, new AssessmentStatus("PROCESSING", null, null));
            
            // Run the actual assessment
            Map<String, Object> result = assessmentService.assess(req, userId);
            
            // Store result
            String resultJson = objectMapper.writeValueAsString(result);
            assessmentCache.put(interviewId, new AssessmentStatus("COMPLETED", resultJson, null));
            
            log.info("Async assessment completed for interview: {}", interviewId);
        } catch (Exception e) {
            log.error("Async assessment failed for interview {}: {}", interviewId, e.getMessage(), e);
            assessmentCache.put(interviewId, new AssessmentStatus("FAILED", null, e.getMessage()));
        }
    }
    
    public AssessmentStatus getAssessmentStatus(String interviewId) {
        return assessmentCache.getOrDefault(interviewId, new AssessmentStatus("NOT_FOUND", null, null));
    }
    
    public void clearAssessmentStatus(String interviewId) {
        assessmentCache.remove(interviewId);
    }

    public static class AssessmentStatus {
        private final String status; // PROCESSING, COMPLETED, FAILED, NOT_FOUND
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
