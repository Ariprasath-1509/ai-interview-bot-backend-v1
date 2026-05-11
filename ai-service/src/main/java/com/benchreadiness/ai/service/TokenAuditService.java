package com.benchreadiness.ai.service;

import com.benchreadiness.ai.client.ComplianceServiceClient;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TokenAuditService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TokenAuditService.class);

    private final ComplianceServiceClient complianceServiceClient;
    private final QuestionService questionService;
    private final AssessmentService assessmentService;

    public TokenAuditService(
            ComplianceServiceClient complianceServiceClient,
            QuestionService questionService,
            AssessmentService assessmentService) {
        this.complianceServiceClient = complianceServiceClient;
        this.questionService = questionService;
        this.assessmentService = assessmentService;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getInterviewAudit(String interviewId) {
        Map<String, Object> summary = new LinkedHashMap<>();
        List<Map<String, Object>> usage = List.of();
        try {
            summary = complianceServiceClient.getInterviewTokenSummary(interviewId);
        } catch (Exception e) {
            log.warn("Failed to fetch interview token summary for {}: {}", interviewId, e.getMessage());
            summary.put("error", "Unable to fetch interview token summary");
        }

        try {
            usage = complianceServiceClient.getInterviewUsage(interviewId);
        } catch (Exception e) {
            log.warn("Failed to fetch interview token usage for {}: {}", interviewId, e.getMessage());
        }

        Map<String, Integer> operationBreakdown = new LinkedHashMap<>();
        int apiCalls = 0;
        for (Map<String, Object> row : usage) {
            if (row == null) continue;
            String op = String.valueOf(row.getOrDefault("operationType", "unknown"));
            operationBreakdown.put(op, operationBreakdown.getOrDefault(op, 0) + 1);
            apiCalls++;
        }

        int questionIdempotencyHits = questionService.getIdempotencyHits(interviewId);
        int assessmentIdempotencyHits = assessmentService.getIdempotencyHits(interviewId);
        int retriesAvoided = questionIdempotencyHits + assessmentIdempotencyHits;

        Map<String, Object> idempotency = new LinkedHashMap<>();
        idempotency.put("questionCacheHits", questionIdempotencyHits);
        idempotency.put("assessmentCacheHits", assessmentIdempotencyHits);
        idempotency.put("retriesAvoided", retriesAvoided);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("interviewId", interviewId);
        result.put("summary", summary);
        result.put("apiCalls", apiCalls);
        result.put("operationBreakdown", operationBreakdown);
        result.put("idempotency", idempotency);
        result.put("usage", usage);
        return result;
    }
}
