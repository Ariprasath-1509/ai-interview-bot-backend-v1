package com.benchreadiness.ops.compliance.service;

import com.benchreadiness.ops.compliance.entity.*;
import com.benchreadiness.ops.compliance.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TokenTrackingService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TokenTrackingService.class);

    private static final Map<String, BigDecimal> MODEL_PRICING = Map.of(
        "claude-haiku-4-5", new BigDecimal("0.00025"),
        "claude-sonnet-4-5", new BigDecimal("0.003"),
        "claude-opus-4-5", new BigDecimal("0.015")
    );

    private final TokenUsageRepository tokenUsageRepository;
    private final DailyTokenLimitRepository dailyTokenLimitRepository;
    private final AssessmentResponseRepository assessmentResponseRepository;
    private final InterviewTokenSummaryRepository interviewTokenSummaryRepository;

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

        BigDecimal pricePerToken = MODEL_PRICING.getOrDefault(modelUsed, new BigDecimal("0.001"));
        BigDecimal cost = pricePerToken.multiply(new BigDecimal(usage.getTotalTokens())).divide(new BigDecimal("1000"));
        usage.setEstimatedCostUsd(cost);

        tokenUsageRepository.save(usage);
    }

    public boolean checkDailyLimit(String userId) {
        DailyTokenLimit limit = dailyTokenLimitRepository.findByOrganizationId("default").orElse(defaultLimit());
        Instant startOfDay = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfNextDay = startOfDay.plus(1, ChronoUnit.DAYS);
        Integer todayUsage = tokenUsageRepository.getTotalTokensForUserAndDate(userId, startOfDay, startOfNextDay);
        return (todayUsage != null ? todayUsage : 0) < limit.getDailyLimit();
    }

    public Map<String, Object> getDailyUsageStatus(String userId) {
        DailyTokenLimit limit = dailyTokenLimitRepository.findByOrganizationId("default").orElse(defaultLimit());
        Instant startOfDay = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfNextDay = startOfDay.plus(1, ChronoUnit.DAYS);
        Integer todayUsage = tokenUsageRepository.getTotalTokensForUserAndDate(userId, startOfDay, startOfNextDay);
        int usage = todayUsage != null ? todayUsage : 0;

        Map<String, Object> result = new HashMap<>();
        result.put("usage", usage);
        result.put("limit", limit.getDailyLimit());
        result.put("warningThreshold", limit.getWarningThreshold());
        result.put("nearLimit", usage >= limit.getWarningThreshold());
        result.put("overLimit", usage >= limit.getDailyLimit());
        result.put("remainingTokens", Math.max(0, limit.getDailyLimit() - usage));
        return result;
    }

    public List<TokenUsage> getInterviewTokenUsage(String interviewId) {
        return tokenUsageRepository.findByInterviewId(interviewId);
    }

    public Map<String, Object> getDailyAnalytics() {
        Instant startOfDay = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfNextDay = startOfDay.plus(1, ChronoUnit.DAYS);
        Integer totalUsage = tokenUsageRepository.getTotalTokensForDate(startOfDay, startOfNextDay);
        List<TokenUsage> todayUsages = tokenUsageRepository.findByDate(startOfDay, startOfNextDay);

        BigDecimal totalCost = todayUsages.stream()
            .map(TokenUsage::getEstimatedCostUsd)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new HashMap<>();
        result.put("date", startOfDay.toString());
        result.put("totalTokens", totalUsage != null ? totalUsage : 0);
        result.put("totalCost", totalCost);
        result.put("totalOperations", todayUsages.size());
        result.put("operationBreakdown", todayUsages.stream()
            .collect(Collectors.groupingBy(TokenUsage::getOperationType, Collectors.counting())));
        return result;
    }

    @Transactional
    public void updateDailyLimit(int newLimit, int newWarningThreshold) {
        DailyTokenLimit limit = dailyTokenLimitRepository.findByOrganizationId("default").orElse(defaultLimit());
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
            .map(r -> {
                Map<String, Object> result = new HashMap<>();
                result.put("interviewId", r.getInterviewId());
                result.put("assessmentJson", r.getAssessmentJson());
                result.put("tokensUsed", r.getTokensUsed());
                result.put("assessmentSource", r.getAssessmentSource());
                result.put("createdAt", r.getCreatedAt());
                result.put("updatedAt", r.getUpdatedAt());
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
        summary.setTotalTokens(usages.stream().mapToInt(TokenUsage::getTotalTokens).sum());
        summary.setTotalCostUsd(usages.stream().map(TokenUsage::getEstimatedCostUsd).reduce(BigDecimal.ZERO, BigDecimal::add));
        summary.setQuestionTokens(usages.stream().filter(u -> "question".equals(u.getOperationType())).mapToInt(TokenUsage::getTotalTokens).sum());
        summary.setAssessmentTokens(usages.stream().filter(u -> "assessment".equals(u.getOperationType())).mapToInt(TokenUsage::getTotalTokens).sum());
        summary.setRubricTokens(usages.stream().filter(u -> "rubric".equals(u.getOperationType())).mapToInt(TokenUsage::getTotalTokens).sum());
        interviewTokenSummaryRepository.save(summary);
    }

    public Map<String, Object> getInterviewTokenSummary(String interviewId) {
        return interviewTokenSummaryRepository.findByInterviewId(interviewId)
            .map(s -> {
                Map<String, Object> result = new HashMap<>();
                result.put("interviewId", s.getInterviewId());
                result.put("totalTokens", s.getTotalTokens());
                result.put("totalCostUsd", s.getTotalCostUsd());
                result.put("questionTokens", s.getQuestionTokens());
                result.put("assessmentTokens", s.getAssessmentTokens());
                result.put("rubricTokens", s.getRubricTokens());
                result.put("createdAt", s.getCreatedAt());
                result.put("updatedAt", s.getUpdatedAt());
                return result;
            })
            .orElse(Map.of("error", "Token summary not found for interview: " + interviewId));
    }

    private DailyTokenLimit defaultLimit() {
        DailyTokenLimit d = new DailyTokenLimit();
        d.setOrganizationId("default");
        d.setDailyLimit(100000);
        d.setWarningThreshold(80000);
        return d;
    }
}
