package com.benchreadiness.compliance.service;

import com.benchreadiness.compliance.entity.AssessmentResponse;
import com.benchreadiness.compliance.entity.DailyTokenLimit;
import com.benchreadiness.compliance.entity.InterviewTokenSummary;
import com.benchreadiness.compliance.entity.TokenUsage;
import com.benchreadiness.compliance.repository.AssessmentResponseRepository;
import com.benchreadiness.compliance.repository.DailyTokenLimitRepository;
import com.benchreadiness.compliance.repository.InterviewTokenSummaryRepository;
import com.benchreadiness.compliance.repository.TokenUsageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Service
public class TokenTrackingService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TokenTrackingService.class);

    private final TokenUsageRepository tokenUsageRepository;
    private final DailyTokenLimitRepository dailyTokenLimitRepository;
    private final AssessmentResponseRepository assessmentResponseRepository;
    private final InterviewTokenSummaryRepository interviewTokenSummaryRepository;

    // Claude pricing (as of 2024) - update when pricing changes
    private static final Map<String, BigDecimal> MODEL_PRICING = Map.of(
        "claude-haiku-4-5", new BigDecimal("0.00025"), // $0.25 per 1K tokens
        "claude-sonnet-4-5", new BigDecimal("0.003"),   // $3.00 per 1K tokens
        "claude-opus-4-5", new BigDecimal("0.015")      // $15.00 per 1K tokens
    );

    public TokenTrackingService(TokenUsageRepository tokenUsageRepository, 
                               DailyTokenLimitRepository dailyTokenLimitRepository,
                               AssessmentResponseRepository assessmentResponseRepository,
                               InterviewTokenSummaryRepository interviewTokenSummaryRepository) {
        this.tokenUsageRepository = tokenUsageRepository;
        this.dailyTokenLimitRepository = dailyTokenLimitRepository;
        this.assessmentResponseRepository = assessmentResponseRepository;
        this.interviewTokenSummaryRepository = interviewTokenSummaryRepository;
    }

    @Transactional
    public void trackTokenUsage(String interviewId, String operationType, String modelUsed, 
                               int promptTokens, int completionTokens, String userId) {
        TokenUsage usage = new TokenUsage();
        usage.setInterviewId(interviewId);
        usage.setOperationType(operationType);
        usage.setModelUsed(modelUsed);
        usage.setPromptTokens(promptTokens);
        usage.setCompletionTokens(completionTokens);
        usage.setTotalTokens(promptTokens + completionTokens);
        usage.setCreatedByUserId(userId);
        
        // Calculate estimated cost
        BigDecimal pricePerToken = MODEL_PRICING.getOrDefault(modelUsed, new BigDecimal("0.001"));
        BigDecimal cost = pricePerToken.multiply(new BigDecimal(usage.getTotalTokens())).divide(new BigDecimal("1000"));
        usage.setEstimatedCostUsd(cost);
        
        tokenUsageRepository.save(usage);
        log.debug("Tracked {} tokens for interview {} ({})", usage.getTotalTokens(), interviewId, operationType);
    }

    public boolean checkDailyLimit(String userId) {
        DailyTokenLimit limit = dailyTokenLimitRepository.findByOrganizationId("default")
                .orElse(getDefaultLimit());
        
        Instant startOfDay = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfNextDay = startOfDay.plus(1, java.time.temporal.ChronoUnit.DAYS);
        Integer todayUsage = tokenUsageRepository.getTotalTokensForUserAndDate(userId, startOfDay, startOfNextDay);
        
        return (todayUsage != null ? todayUsage : 0) < limit.getDailyLimit();
    }

    public Map<String, Object> getDailyUsageStatus(String userId) {
        DailyTokenLimit limit = dailyTokenLimitRepository.findByOrganizationId("default")
                .orElse(getDefaultLimit());
        
        Instant startOfDay = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfNextDay = startOfDay.plus(1, java.time.temporal.ChronoUnit.DAYS);
        Integer todayUsage = tokenUsageRepository.getTotalTokensForUserAndDate(userId, startOfDay, startOfNextDay);
        int usage = todayUsage != null ? todayUsage : 0;
        
        boolean nearLimit = usage >= limit.getWarningThreshold();
        boolean overLimit = usage >= limit.getDailyLimit();
        
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("usage", usage);
        result.put("limit", limit.getDailyLimit());
        result.put("warningThreshold", limit.getWarningThreshold());
        result.put("nearLimit", nearLimit);
        result.put("overLimit", overLimit);
        result.put("remainingTokens", Math.max(0, limit.getDailyLimit() - usage));
        return result;
    }

    public List<TokenUsage> getInterviewTokenUsage(String interviewId) {
        return tokenUsageRepository.findByInterviewId(interviewId);
    }

    public Map<String, Object> getDailyAnalytics() {
        Instant startOfDay = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfNextDay = startOfDay.plus(1, java.time.temporal.ChronoUnit.DAYS);
        Integer totalUsage = tokenUsageRepository.getTotalTokensForDate(startOfDay, startOfNextDay);
        List<TokenUsage> todayUsages = tokenUsageRepository.findByDate(startOfDay, startOfNextDay);
        
        BigDecimal totalCost = todayUsages.stream()
                .map(TokenUsage::getEstimatedCostUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("date", startOfDay.toString());
        result.put("totalTokens", totalUsage != null ? totalUsage : 0);
        result.put("totalCost", totalCost);
        result.put("totalOperations", todayUsages.size());
        result.put("operationBreakdown", getOperationBreakdown(todayUsages));
        return result;
    }

    private Map<String, Long> getOperationBreakdown(List<TokenUsage> usages) {
        return usages.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    TokenUsage::getOperationType,
                    java.util.stream.Collectors.counting()
                ));
    }

    private DailyTokenLimit getDefaultLimit() {
        DailyTokenLimit defaultLimit = new DailyTokenLimit();
        defaultLimit.setOrganizationId("default");
        defaultLimit.setDailyLimit(100000);
        defaultLimit.setWarningThreshold(80000);
        return defaultLimit;
    }

    @Transactional
    public void updateDailyLimit(int newLimit, int newWarningThreshold) {
        DailyTokenLimit limit = dailyTokenLimitRepository.findByOrganizationId("default")
                .orElse(getDefaultLimit());
        limit.setDailyLimit(newLimit);
        limit.setWarningThreshold(newWarningThreshold);
        dailyTokenLimitRepository.save(limit);
    }

    @Transactional
    public void storeAssessmentResponse(String interviewId, String assessmentJson, Integer tokensUsed, String assessmentSource) {
        AssessmentResponse response = assessmentResponseRepository.findByInterviewId(interviewId)
                .orElse(new AssessmentResponse());
        
        response.setInterviewId(interviewId);
        response.setAssessmentJson(assessmentJson);
        response.setTokensUsed(tokensUsed != null ? tokensUsed : 0);
        response.setAssessmentSource(assessmentSource != null ? assessmentSource : "claude-two-pass");
        
        assessmentResponseRepository.save(response);
        log.info("Stored assessment response for interview {} with {} tokens", interviewId, tokensUsed);
    }

    public Map<String, Object> getAssessmentResponse(String interviewId) {
        return assessmentResponseRepository.findByInterviewId(interviewId)
                .map(response -> {
                    Map<String, Object> result = new java.util.HashMap<>();
                    result.put("interviewId", response.getInterviewId());
                    result.put("assessmentJson", response.getAssessmentJson());
                    result.put("tokensUsed", response.getTokensUsed());
                    result.put("assessmentSource", response.getAssessmentSource());
                    result.put("createdAt", response.getCreatedAt());
                    result.put("updatedAt", response.getUpdatedAt());
                    return result;
                })
                .orElse(Map.of("error", "Assessment response not found for interview: " + interviewId));
    }

    @Transactional
    public void finalizeInterviewTokenSummary(String interviewId) {
        List<TokenUsage> usages = tokenUsageRepository.findByInterviewId(interviewId);
        
        if (usages.isEmpty()) {
            log.warn("No token usage found for interview {}", interviewId);
            return;
        }
        
        InterviewTokenSummary summary = interviewTokenSummaryRepository.findByInterviewId(interviewId)
                .orElse(new InterviewTokenSummary());
        
        summary.setInterviewId(interviewId);
        
        int totalTokens = usages.stream().mapToInt(TokenUsage::getTotalTokens).sum();
        BigDecimal totalCost = usages.stream()
                .map(TokenUsage::getEstimatedCostUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        int questionTokens = usages.stream()
                .filter(u -> "question".equals(u.getOperationType()))
                .mapToInt(TokenUsage::getTotalTokens).sum();
        
        int assessmentTokens = usages.stream()
                .filter(u -> "assessment".equals(u.getOperationType()))
                .mapToInt(TokenUsage::getTotalTokens).sum();
        
        int rubricTokens = usages.stream()
                .filter(u -> "rubric".equals(u.getOperationType()))
                .mapToInt(TokenUsage::getTotalTokens).sum();
        
        summary.setTotalTokens(totalTokens);
        summary.setTotalCostUsd(totalCost);
        summary.setQuestionTokens(questionTokens);
        summary.setAssessmentTokens(assessmentTokens);
        summary.setRubricTokens(rubricTokens);
        
        interviewTokenSummaryRepository.save(summary);
        log.info("Finalized token summary for interview {}: {} total tokens, ${} cost", 
                interviewId, totalTokens, totalCost);
    }

    public Map<String, Object> getInterviewTokenSummary(String interviewId) {
        return interviewTokenSummaryRepository.findByInterviewId(interviewId)
                .map(summary -> {
                    Map<String, Object> result = new java.util.HashMap<>();
                    result.put("interviewId", summary.getInterviewId());
                    result.put("totalTokens", summary.getTotalTokens());
                    result.put("totalCostUsd", summary.getTotalCostUsd());
                    result.put("questionTokens", summary.getQuestionTokens());
                    result.put("assessmentTokens", summary.getAssessmentTokens());
                    result.put("rubricTokens", summary.getRubricTokens());
                    result.put("createdAt", summary.getCreatedAt());
                    result.put("updatedAt", summary.getUpdatedAt());
                    return result;
                })
                .orElse(Map.of("error", "Token summary not found for interview: " + interviewId));
    }
}