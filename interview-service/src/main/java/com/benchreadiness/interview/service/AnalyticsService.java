package com.benchreadiness.interview.service;

import com.benchreadiness.interview.entity.*;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final InterviewRepository interviewRepository;
    private final EngineerRepository engineerRepository;
    private final AuthServiceClient authServiceClient;

    public AnalyticsService(InterviewRepository interviewRepository, 
                           EngineerRepository engineerRepository,
                           AuthServiceClient authServiceClient) {
        this.interviewRepository = interviewRepository;
        this.engineerRepository = engineerRepository;
        this.authServiceClient = authServiceClient;
    }

    public Map<String, Object> getRealTimeAnalytics(String userId, String userRole) {
        // Get all interviews or user-specific based on role
        java.util.List<Interview> interviews = getUserInterviews(userId, userRole);
        
        // Count by status
        long scheduled = interviews.stream().filter(i -> i.getStatus() == InterviewStatus.SCHEDULED).count();
        long inProgress = interviews.stream().filter(i -> i.getStatus() == InterviewStatus.IN_PROGRESS).count();
        long completed = interviews.stream().filter(i -> i.getStatus() == InterviewStatus.COMPLETED).count();
        long signedOff = interviews.stream().filter(i -> i.getStatus() == InterviewStatus.SIGNED_OFF).count();
        
        // Today's interviews
        Instant startOfDay = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endOfDay = startOfDay.plusSeconds(24 * 3600);
        long todayInterviews = interviews.stream()
                .filter(i -> i.getCreatedAt().isAfter(startOfDay) && i.getCreatedAt().isBefore(endOfDay))
                .count();
        
        // This week's interviews
        Instant startOfWeek = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() - 1)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        long weekInterviews = interviews.stream()
                .filter(i -> i.getCreatedAt().isAfter(startOfWeek))
                .count();
        
        // This month's interviews
        Instant startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        long monthInterviews = interviews.stream()
                .filter(i -> i.getCreatedAt().isAfter(startOfMonth))
                .count();
        
        // Success rate (READY verdicts)
        long readyCount = interviews.stream()
                .filter(i -> i.getFinalVerdict() != null)
                .filter(i -> "READY".equals(i.getFinalVerdict().name()))
                .count();
        long totalAssessed = interviews.stream()
                .filter(i -> i.getFinalVerdict() != null)
                .count();
        double successRate = totalAssessed > 0 ? (double) readyCount / totalAssessed * 100 : 0;
        
        Map<String, Object> result = new HashMap<>();
        
        Map<String, Object> statusCounts = new HashMap<>();
        statusCounts.put("scheduled", scheduled);
        statusCounts.put("inProgress", inProgress);
        statusCounts.put("completed", completed);
        statusCounts.put("signedOff", signedOff);
        statusCounts.put("total", interviews.size());
        
        Map<String, Object> timePeriods = new HashMap<>();
        timePeriods.put("today", todayInterviews);
        timePeriods.put("thisWeek", weekInterviews);
        timePeriods.put("thisMonth", monthInterviews);
        timePeriods.put("total", interviews.size());
        
        Map<String, Object> successMetrics = new HashMap<>();
        successMetrics.put("readyCount", readyCount);
        successMetrics.put("totalAssessed", totalAssessed);
        successMetrics.put("successRate", Math.round(successRate * 100.0) / 100.0);
        
        result.put("statusCounts", statusCounts);
        result.put("timePeriods", timePeriods);
        result.put("successMetrics", successMetrics);
        result.put("lastUpdated", Instant.now().toString());
        result.put("hasRealData", interviews.size() > 0); // Add flag for real data
        
        return result;
    }

    public Map<String, Object> getVerdictAnalytics(String userId, String userRole) {
        List<Interview> interviews = getUserInterviews(userId, userRole);
        
        Map<String, Long> verdictDistribution = new LinkedHashMap<>();
        verdictDistribution.put("READY", 0L);
        verdictDistribution.put("NEEDS_1_WEEK_PREP", 0L);
        verdictDistribution.put("NEEDS_RESKILLING", 0L);
        verdictDistribution.put("MISMATCH_WITH_JD", 0L);
        verdictDistribution.put("WITHDRAWN", 0L);
        
        long totalAssessed = interviews.stream()
                .filter(i -> i.getFinalVerdict() != null)
                .count();
        
        interviews.stream()
                .filter(i -> i.getFinalVerdict() != null)
                .forEach(i -> {
                    String verdict = i.getFinalVerdict().name();
                    verdictDistribution.put(verdict, verdictDistribution.get(verdict) + 1);
                });
        
        Map<String, Object> result = new HashMap<>();
        result.put("verdictDistribution", verdictDistribution);
        result.put("totalAssessed", totalAssessed);
        result.put("hasRealData", totalAssessed > 0); // Add flag to indicate real vs empty data
        return result;
    }

    public Map<String, Object> getInterviewerAnalytics(String userId, String userRole) {
        List<Interview> interviews = getUserInterviews(userId, userRole);
        
        // Group interviews by creator
        Map<String, List<Interview>> interviewsByCreator = interviews.stream()
                .filter(i -> i.getCreatedByUserId() != null)
                .collect(Collectors.groupingBy(Interview::getCreatedByUserId));
        
        List<Map<String, Object>> topInterviewers = interviewsByCreator.entrySet().stream()
                .map(entry -> {
                    String creatorId = entry.getKey();
                    List<Interview> creatorInterviews = entry.getValue();
                    
                    long totalInterviews = creatorInterviews.size();
                    long readyCount = creatorInterviews.stream()
                            .filter(i -> i.getFinalVerdict() == ReadinessVerdict.READY)
                            .count();
                    long assessedCount = creatorInterviews.stream()
                            .filter(i -> i.getFinalVerdict() != null)
                            .count();
                    
                    double successRate = assessedCount > 0 ? (double) readyCount / assessedCount * 100 : 0;
                    
                    // Get user details from auth service
                    Map<String, Object> userDetails = authServiceClient.getUserById(creatorId);
                    
                    Map<String, Object> interviewer = new HashMap<>();
                    interviewer.put("name", userDetails.get("name"));
                    interviewer.put("email", userDetails.get("email"));
                    interviewer.put("interviewCount", (int) totalInterviews);
                    interviewer.put("successRate", Math.round(successRate * 100.0) / 100.0);
                    return interviewer;
                })
                .sorted((a, b) -> Integer.compare((Integer) b.get("interviewCount"), (Integer) a.get("interviewCount")))
                .limit(10)
                .collect(Collectors.toList());
        
        // If no real data, return empty list
        if (topInterviewers.isEmpty()) {
            topInterviewers = List.of();
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("topInterviewers", topInterviewers);
        result.put("hasData", !topInterviewers.isEmpty());
        return result;
    }

    public Map<String, Object> getTrendAnalytics(String userId, String userRole) {
        List<Interview> interviews = getUserInterviews(userId, userRole);
        
        // Daily trends for last 7 days
        List<Map<String, Object>> dailyTrends = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            Instant startOfDay = date.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant endOfDay = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            
            long dayInterviews = interviews.stream()
                    .filter(interview -> interview.getCreatedAt().isAfter(startOfDay) && interview.getCreatedAt().isBefore(endOfDay))
                    .count();
            
            long dayCompleted = interviews.stream()
                    .filter(interview -> interview.getCreatedAt().isAfter(startOfDay) && interview.getCreatedAt().isBefore(endOfDay))
                    .filter(interview -> interview.getStatus() == InterviewStatus.COMPLETED || interview.getStatus() == InterviewStatus.SIGNED_OFF)
                    .count();
            
            long dayReady = interviews.stream()
                    .filter(interview -> interview.getCreatedAt().isAfter(startOfDay) && interview.getCreatedAt().isBefore(endOfDay))
                    .filter(interview -> interview.getFinalVerdict() == ReadinessVerdict.READY)
                    .count();
            
            double successRate = dayCompleted > 0 ? (double) dayReady / dayCompleted * 100 : 0;
            
            Map<String, Object> dayTrend = new HashMap<>();
            dayTrend.put("date", date.format(DateTimeFormatter.ISO_LOCAL_DATE));
            dayTrend.put("interviews", dayInterviews);
            dayTrend.put("completed", dayCompleted);
            dayTrend.put("successRate", Math.round(successRate * 100.0) / 100.0);
            
            dailyTrends.add(dayTrend);
        }
        
        // Weekly trends for last 4 weeks
        List<Map<String, Object>> weeklyTrends = new ArrayList<>();
        for (int i = 3; i >= 0; i--) {
            LocalDate weekStart = LocalDate.now().minusWeeks(i).minusDays(LocalDate.now().getDayOfWeek().getValue() - 1);
            LocalDate weekEnd = weekStart.plusDays(6);
            Instant startOfWeek = weekStart.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant endOfWeek = weekEnd.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            
            long weekInterviews = interviews.stream()
                    .filter(interview -> interview.getCreatedAt().isAfter(startOfWeek) && interview.getCreatedAt().isBefore(endOfWeek))
                    .count();
            
            long weekCompleted = interviews.stream()
                    .filter(interview -> interview.getCreatedAt().isAfter(startOfWeek) && interview.getCreatedAt().isBefore(endOfWeek))
                    .filter(interview -> interview.getStatus() == InterviewStatus.COMPLETED || interview.getStatus() == InterviewStatus.SIGNED_OFF)
                    .count();
            
            long weekReady = interviews.stream()
                    .filter(interview -> interview.getCreatedAt().isAfter(startOfWeek) && interview.getCreatedAt().isBefore(endOfWeek))
                    .filter(interview -> interview.getFinalVerdict() == ReadinessVerdict.READY)
                    .count();
            
            double successRate = weekCompleted > 0 ? (double) weekReady / weekCompleted * 100 : 0;
            
            Map<String, Object> weekTrend = new HashMap<>();
            weekTrend.put("week", weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE) + " to " + weekEnd.format(DateTimeFormatter.ISO_LOCAL_DATE));
            weekTrend.put("interviews", weekInterviews);
            weekTrend.put("completed", weekCompleted);
            weekTrend.put("successRate", Math.round(successRate * 100.0) / 100.0);
            
            weeklyTrends.add(weekTrend);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("dailyTrends", dailyTrends);
        result.put("weeklyTrends", weeklyTrends);
        return result;
    }

    public Map<String, Object> getInterviewModeAnalytics(String userId, String userRole) {
        java.util.List<Interview> interviews = getUserInterviews(userId, userRole);
        
        Map<String, Long> modeDistribution = interviews.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    i -> i.getInterviewMode() != null ? i.getInterviewMode().name() : "UNKNOWN",
                    java.util.stream.Collectors.counting()
                ));
        
        Map<String, Object> result = new HashMap<>();
        result.put("modeDistribution", modeDistribution);
        result.put("totalInterviews", interviews.size());
        return result;
    }

    private java.util.List<Interview> getUserInterviews(String userId, String userRole) {
        // ADMIN and BENCH_MANAGER see all interviews
        if ("ADMIN".equals(userRole) || "BENCH_MANAGER".equals(userRole)) {
            return interviewRepository.findAll();
        }
        
        // INTERVIEWER sees only interviews they created
        if ("INTERVIEWER".equals(userRole)) {
            return interviewRepository.findAll().stream()
                    .filter(i -> userId.equals(i.getCreatedByUserId()))
                    .collect(Collectors.toList());
        }
        
        // HR and COMPLIANCE see all interviews (read-only)
        return interviewRepository.findAll();
    }

    public Map<String, Object> getDebugInfo(String userId, String userRole) {
        List<Interview> allInterviews = interviewRepository.findAll();
        List<Interview> userInterviews = getUserInterviews(userId, userRole);
        
        // Log the filtering logic
        System.out.println("DEBUG - userId: " + userId);
        System.out.println("DEBUG - userRole: " + userRole);
        System.out.println("DEBUG - Total interviews in DB: " + allInterviews.size());
        System.out.println("DEBUG - User visible interviews: " + userInterviews.size());
        
        Map<String, Object> debug = new HashMap<>();
        debug.put("userId", userId);
        debug.put("userRole", userRole);
        debug.put("totalInterviewsInDB", allInterviews.size());
        debug.put("userVisibleInterviews", userInterviews.size());
        debug.put("interviewsWithCreatedBy", allInterviews.stream().filter(i -> i.getCreatedByUserId() != null).count());
        debug.put("interviewsWithVerdict", allInterviews.stream().filter(i -> i.getFinalVerdict() != null).count());
        
        // Sample of interviews with created_by info
        debug.put("sampleInterviews", allInterviews.stream().limit(5).map(i -> {
            Map<String, Object> sample = new HashMap<>();
            sample.put("id", i.getId());
            sample.put("status", i.getStatus());
            sample.put("createdBy", i.getCreatedByUserId());
            sample.put("verdict", i.getFinalVerdict());
            sample.put("createdAt", i.getCreatedAt());
            sample.put("engineerId", i.getEngineerId());
            return sample;
        }).toList());
        
        return debug;
    }
}