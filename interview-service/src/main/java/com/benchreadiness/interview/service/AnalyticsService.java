package com.benchreadiness.interview.service;

import com.benchreadiness.interview.entity.*;
import com.benchreadiness.interview.repository.*;

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
    private final JobDescriptionRepository jdRepository;
    private final com.benchreadiness.interview.client.AuthServiceClient authServiceClient;
    private final com.benchreadiness.interview.client.ReviewServiceClient reviewServiceClient;

    public AnalyticsService(InterviewRepository interviewRepository, 
                           EngineerRepository engineerRepository,
                           JobDescriptionRepository jdRepository,
                           com.benchreadiness.interview.client.AuthServiceClient authServiceClient,
                           com.benchreadiness.interview.client.ReviewServiceClient reviewServiceClient) {
        this.interviewRepository = interviewRepository;
        this.engineerRepository = engineerRepository;
        this.jdRepository = jdRepository;
        this.authServiceClient = authServiceClient;
        this.reviewServiceClient = reviewServiceClient;
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
                    Map<String, Object> userDetails;
                    try {
                        userDetails = authServiceClient.getUserById(creatorId);
                    } catch (Exception e) {
                        // Fallback if auth service is unavailable
                        userDetails = Map.of(
                            "name", "User " + creatorId.substring(0, Math.min(8, creatorId.length())),
                            "email", creatorId + "@company.com"
                        );
                    }
                    
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
        // SUPER_ADMIN and ADMIN see all interviews
        if ("SUPER_ADMIN".equals(userRole) || "ADMIN".equals(userRole)) {
            return interviewRepository.findAll();
        }
        
        // RECRUITER sees only interviews they created
        if ("RECRUITER".equals(userRole)) {
            return interviewRepository.findAll().stream()
                    .filter(i -> userId.equals(i.getCreatedByUserId()))
                    .collect(Collectors.toList());
        }
        
        // Default: return all interviews
        return interviewRepository.findAll();
    }

    public Map<String, Object> getCandidatePerformanceAnalytics(String userId, String userRole) {
        List<Interview> interviews = getUserInterviews(userId, userRole);
        
        // Filter to completed interviews with assessments
        List<Interview> assessedInterviews = interviews.stream()
                .filter(i -> i.getStatus() == InterviewStatus.COMPLETED || i.getStatus() == InterviewStatus.SIGNED_OFF)
                .filter(i -> i.getFinalVerdict() != null)
                .collect(Collectors.toList());
        
        // Candidate performance by verdict
        Map<String, Long> performanceByVerdict = new LinkedHashMap<>();
        performanceByVerdict.put("READY", 0L);
        performanceByVerdict.put("NEEDS_1_WEEK_PREP", 0L);
        performanceByVerdict.put("NEEDS_RESKILLING", 0L);
        performanceByVerdict.put("MISMATCH_WITH_JD", 0L);
        performanceByVerdict.put("WITHDRAWN", 0L);
        
        assessedInterviews.forEach(i -> {
            String verdict = i.getFinalVerdict().name();
            performanceByVerdict.put(verdict, performanceByVerdict.get(verdict) + 1);
        });
        
        // Performance by interview mode
        Map<String, Map<String, Object>> performanceByMode = new LinkedHashMap<>();
        for (InterviewMode mode : InterviewMode.values()) {
            List<Interview> modeInterviews = assessedInterviews.stream()
                    .filter(i -> i.getInterviewMode() == mode)
                    .collect(Collectors.toList());
            
            long total = modeInterviews.size();
            long ready = modeInterviews.stream()
                    .filter(i -> i.getFinalVerdict() == ReadinessVerdict.READY)
                    .count();
            double successRate = total > 0 ? (double) ready / total * 100 : 0;
            
            Map<String, Object> modeStats = new HashMap<>();
            modeStats.put("totalCandidates", total);
            modeStats.put("readyCandidates", ready);
            modeStats.put("successRate", Math.round(successRate * 100.0) / 100.0);
            
            performanceByMode.put(mode.name(), modeStats);
        }
        
        // Top performing candidates (highest average scores)
        List<Map<String, Object>> topCandidates = assessedInterviews.stream()
                .map(interview -> {
                    // Get engineer details
                    Engineer engineer = engineerRepository.findById(interview.getEngineerId()).orElse(null);
                    JobDescription jd = jdRepository.findById(interview.getJdId()).orElse(null);
                    
                    String candidateName = engineer != null && engineer.getName() != null && !engineer.getName().isBlank()
                            ? engineer.getName()
                            : (engineer != null ? engineer.getEmail() : "Unknown");
                    String candidateEmail = engineer != null ? engineer.getEmail() : "";
                    String jdTitle = jd != null ? jd.getTitle() : "";
                    
                    List<Map<String, Object>> scores;
                    try {
                        scores = reviewServiceClient.getScores(interview.getId());
                    } catch (Exception e) {
                        System.err.println("Failed to get scores for interview " + interview.getId() + ": " + e.getMessage());
                        scores = List.of();
                    }
                    
                    double avgScore = scores.stream()
                            .mapToInt(score -> (Integer) score.get("value"))
                            .average()
                            .orElse(0.0);
                    
                    Map<String, Object> candidate = new HashMap<>();
                    candidate.put("candidateName", candidateName);
                    candidate.put("candidateEmail", candidateEmail);
                    candidate.put("interviewMode", interview.getInterviewMode().name());
                    candidate.put("averageScore", Math.round(avgScore * 100.0) / 100.0);
                    candidate.put("verdict", interview.getFinalVerdict().name());
                    candidate.put("jdTitle", jdTitle);
                    candidate.put("interviewDate", interview.getCreatedAt().toString().substring(0, 10));
                    
                    return candidate;
                })
                .sorted((a, b) -> Double.compare((Double) b.get("averageScore"), (Double) a.get("averageScore")))
                .limit(10)
                .collect(Collectors.toList());
        
        // Skill gap analysis - most common weak areas
        Map<String, Integer> skillGaps = new HashMap<>();
        assessedInterviews.forEach(interview -> {
            List<Map<String, Object>> scores;
            try {
                scores = reviewServiceClient.getScores(interview.getId());
            } catch (Exception e) {
                System.err.println("Failed to get scores for interview " + interview.getId() + ": " + e.getMessage());
                scores = List.of();
            }
            scores.stream()
                    .filter(score -> (Integer) score.get("value") < 3) // Below average performance
                    .forEach(score -> {
                        String dimension = (String) score.get("dimension");
                        skillGaps.put(dimension, skillGaps.getOrDefault(dimension, 0) + 1);
                    });
        });
        
        List<Map<String, Object>> commonWeaknesses = skillGaps.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(entry -> {
                    Map<String, Object> weakness = new HashMap<>();
                    weakness.put("skill", entry.getKey());
                    weakness.put("candidateCount", entry.getValue());
                    weakness.put("percentage", Math.round((double) entry.getValue() / assessedInterviews.size() * 100 * 100.0) / 100.0);
                    return weakness;
                })
                .collect(Collectors.toList());
        
        // Average scores by skill dimension
        Map<String, Double> avgScoresByDimension = new HashMap<>();
        Map<String, Integer> dimensionCounts = new HashMap<>();
        
        assessedInterviews.forEach(interview -> {
            List<Map<String, Object>> scores;
            try {
                scores = reviewServiceClient.getScores(interview.getId());
            } catch (Exception e) {
                System.err.println("Failed to get scores for interview " + interview.getId() + ": " + e.getMessage());
                scores = List.of();
            }
            scores.forEach(score -> {
                String dimension = (String) score.get("dimension");
                Integer value = (Integer) score.get("value");
                
                avgScoresByDimension.put(dimension, avgScoresByDimension.getOrDefault(dimension, 0.0) + value);
                dimensionCounts.put(dimension, dimensionCounts.getOrDefault(dimension, 0) + 1);
            });
        });
        
        Map<String, Double> finalAvgScores = avgScoresByDimension.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> Math.round(entry.getValue() / dimensionCounts.get(entry.getKey()) * 100.0) / 100.0
                ));
        
        Map<String, Object> result = new HashMap<>();
        result.put("performanceByVerdict", performanceByVerdict);
        result.put("performanceByMode", performanceByMode);
        result.put("topCandidates", topCandidates);
        result.put("commonWeaknesses", commonWeaknesses);
        result.put("averageScoresBySkill", finalAvgScores);
        result.put("totalAssessedCandidates", assessedInterviews.size());
        result.put("overallSuccessRate", assessedInterviews.size() > 0 ? 
            Math.round((double) performanceByVerdict.get("READY") / assessedInterviews.size() * 100 * 100.0) / 100.0 : 0);
        result.put("hasData", !assessedInterviews.isEmpty());
        
        return result;
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

    public Map<String, Object> getDailyReportData(String userId, String userRole) {
        Instant startOfDay = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endOfDay = startOfDay.plusSeconds(24 * 3600);
        
        List<Interview> allInterviews = interviewRepository.findAll();
        List<Interview> todayInterviews = allInterviews.stream()
                .filter(i -> i.getCreatedAt().isAfter(startOfDay) && i.getCreatedAt().isBefore(endOfDay))
                .collect(Collectors.toList());
        
        Map<String, Object> report = new HashMap<>();
        
        // 1. Interview Metrics
        Map<String, Object> interviewMetrics = new HashMap<>();
        interviewMetrics.put("totalToday", todayInterviews.size());
        interviewMetrics.put("scheduled", todayInterviews.stream().filter(i -> i.getStatus() == InterviewStatus.SCHEDULED).count());
        interviewMetrics.put("inProgress", todayInterviews.stream().filter(i -> i.getStatus() == InterviewStatus.IN_PROGRESS).count());
        interviewMetrics.put("completed", todayInterviews.stream().filter(i -> i.getStatus() == InterviewStatus.COMPLETED).count());
        interviewMetrics.put("reviewPending", todayInterviews.stream().filter(i -> i.getStatus() == InterviewStatus.REVIEW_PENDING).count());
        interviewMetrics.put("signedOff", todayInterviews.stream().filter(i -> i.getStatus() == InterviewStatus.SIGNED_OFF).count());
        
        // Mode distribution
        Map<String, Long> modeDistribution = todayInterviews.stream()
                .collect(Collectors.groupingBy(
                    i -> i.getInterviewMode() != null ? i.getInterviewMode().name() : "UNKNOWN",
                    Collectors.counting()
                ));
        interviewMetrics.put("modeDistribution", modeDistribution);
        
        // Success metrics
        long readyCount = todayInterviews.stream()
                .filter(i -> i.getFinalVerdict() == ReadinessVerdict.READY)
                .count();
        long totalAssessed = todayInterviews.stream()
                .filter(i -> i.getFinalVerdict() != null)
                .count();
        double successRate = totalAssessed > 0 ? (double) readyCount / totalAssessed * 100 : 0;
        interviewMetrics.put("readyCount", readyCount);
        interviewMetrics.put("totalAssessed", totalAssessed);
        interviewMetrics.put("successRate", Math.round(successRate * 100.0) / 100.0);
        
        report.put("interviewMetrics", interviewMetrics);
        
        // 2. Malpractice & Violations
        Map<String, Object> violations = new HashMap<>();
        long withdrawnCount = todayInterviews.stream()
                .filter(i -> i.getProposedVerdict() == ReadinessVerdict.WITHDRAWN || i.getFinalVerdict() == ReadinessVerdict.WITHDRAWN)
                .count();
        violations.put("totalWithdrawn", withdrawnCount);
        
        // Parse transcripts for violation reasons
        List<Map<String, Object>> violationDetails = todayInterviews.stream()
                .filter(i -> i.getProposedVerdict() == ReadinessVerdict.WITHDRAWN || i.getFinalVerdict() == ReadinessVerdict.WITHDRAWN)
                .map(i -> {
                    Engineer engineer = engineerRepository.findById(i.getEngineerId()).orElse(null);
                    String candidateName = engineer != null ? engineer.getName() : "Unknown";
                    String candidateEmail = engineer != null ? engineer.getEmail() : "";
                    
                    String reason = "Unknown";
                    if (i.getTranscriptJson() != null && i.getTranscriptJson().contains("tab switching")) {
                        reason = "Tab Switching Violation";
                    } else if (i.getTranscriptJson() != null && i.getTranscriptJson().contains("AI manipulation")) {
                        reason = "AI Manipulation";
                    } else if (i.getTranscriptJson() != null && i.getTranscriptJson().contains("not prepared")) {
                        reason = "Not Prepared";
                    } else if (i.getTranscriptJson() != null && i.getTranscriptJson().contains("time expired")) {
                        reason = "Time Expired";
                    }
                    
                    Map<String, Object> detail = new HashMap<>();
                    detail.put("candidateName", candidateName);
                    detail.put("candidateEmail", candidateEmail);
                    detail.put("reason", reason);
                    detail.put("interviewId", i.getId());
                    detail.put("timestamp", i.getEndedAt() != null ? i.getEndedAt().toString() : i.getCreatedAt().toString());
                    return detail;
                })
                .collect(Collectors.toList());
        violations.put("details", violationDetails);
        
        report.put("violations", violations);
        
        // 3. Interviews Pending Review
        Instant oneDayAgo = Instant.now().minusSeconds(24 * 3600);
        List<Map<String, Object>> pendingReviews = allInterviews.stream()
                .filter(i -> i.getStatus() == InterviewStatus.REVIEW_PENDING)
                .filter(i -> i.getEndedAt() != null && i.getEndedAt().isBefore(oneDayAgo))
                .map(i -> {
                    Engineer engineer = engineerRepository.findById(i.getEngineerId()).orElse(null);
                    JobDescription jd = jdRepository.findById(i.getJdId()).orElse(null);
                    
                    Map<String, Object> pending = new HashMap<>();
                    pending.put("interviewId", i.getId());
                    pending.put("candidateName", engineer != null ? engineer.getName() : "Unknown");
                    pending.put("jdTitle", jd != null ? jd.getTitle() : "Unknown");
                    pending.put("completedAt", i.getEndedAt().toString());
                    pending.put("hoursWaiting", (Instant.now().getEpochSecond() - i.getEndedAt().getEpochSecond()) / 3600);
                    return pending;
                })
                .collect(Collectors.toList());
        
        Map<String, Object> alerts = new HashMap<>();
        alerts.put("pendingReviewCount", pendingReviews.size());
        alerts.put("pendingReviews", pendingReviews);
        
        report.put("alerts", alerts);
        
        // 4. Today's Interview List
        List<Map<String, Object>> interviewList = todayInterviews.stream()
                .map(i -> {
                    Engineer engineer = engineerRepository.findById(i.getEngineerId()).orElse(null);
                    JobDescription jd = jdRepository.findById(i.getJdId()).orElse(null);
                    
                    Map<String, Object> interview = new HashMap<>();
                    interview.put("id", i.getId());
                    interview.put("candidateName", engineer != null ? engineer.getName() : "Unknown");
                    interview.put("candidateEmail", engineer != null ? engineer.getEmail() : "");
                    interview.put("jdTitle", jd != null ? jd.getTitle() : "Unknown");
                    interview.put("status", i.getStatus().name());
                    interview.put("mode", i.getInterviewMode() != null ? i.getInterviewMode().name() : "UNKNOWN");
                    interview.put("proposedVerdict", i.getProposedVerdict() != null ? i.getProposedVerdict().name() : null);
                    interview.put("finalVerdict", i.getFinalVerdict() != null ? i.getFinalVerdict().name() : null);
                    interview.put("createdAt", i.getCreatedAt().toString());
                    return interview;
                })
                .collect(Collectors.toList());
        
        report.put("interviewList", interviewList);
        report.put("reportDate", LocalDate.now().toString());
        report.put("generatedAt", Instant.now().toString());
        
        return report;
    }
}