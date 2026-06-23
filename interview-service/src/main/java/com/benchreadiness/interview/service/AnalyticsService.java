package com.benchreadiness.interview.service;

import com.benchreadiness.interview.branch.Branch;
import com.benchreadiness.interview.branch.BranchAccess;
import com.benchreadiness.interview.branch.InterviewBranchFilter;
import com.benchreadiness.interview.branch.InterviewCandidateBranchLookup;
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
    private final ClientRepository clientRepository;
    private final com.benchreadiness.interview.client.AuthServiceClient authServiceClient;
    private final com.benchreadiness.interview.client.ReviewServiceClient reviewServiceClient;
    private final InterviewCandidateBranchLookup candidateBranchLookup;

    public AnalyticsService(InterviewRepository interviewRepository,
                           EngineerRepository engineerRepository,
                           JobDescriptionRepository jdRepository,
                           ClientRepository clientRepository,
                           com.benchreadiness.interview.client.AuthServiceClient authServiceClient,
                           com.benchreadiness.interview.client.ReviewServiceClient reviewServiceClient,
                           InterviewCandidateBranchLookup candidateBranchLookup) {
        this.interviewRepository = interviewRepository;
        this.engineerRepository = engineerRepository;
        this.jdRepository = jdRepository;
        this.clientRepository = clientRepository;
        this.authServiceClient = authServiceClient;
        this.reviewServiceClient = reviewServiceClient;
        this.candidateBranchLookup = candidateBranchLookup;
    }

    public Map<String, Object> getRealTimeAnalytics(String userId, String userRole) {
        // Get all interviews or user-specific based on role
        java.util.List<Interview> interviews = getUserInterviews(userId, userRole);
        
        // Count by status
        long draft = interviews.stream().filter(i -> i.getStatus() == InterviewStatus.DRAFT).count();
        long scheduled = interviews.stream().filter(i -> i.getStatus() == InterviewStatus.SCHEDULED).count();
        long inProgress = interviews.stream().filter(i -> i.getStatus() == InterviewStatus.IN_PROGRESS).count();
        long completed = interviews.stream().filter(i -> i.getStatus() == InterviewStatus.COMPLETED).count();
        long signedOff = interviews.stream().filter(i -> i.getStatus() == InterviewStatus.SIGNED_OFF).count();
        long withdrawn = interviews.stream()
                .filter(i -> i.getProposedVerdict() == ReadinessVerdict.WITHDRAWN
                        || i.getFinalVerdict() == ReadinessVerdict.WITHDRAWN)
                .count();
        
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
        statusCounts.put("draft", draft);
        statusCounts.put("scheduled", scheduled);
        statusCounts.put("inProgress", inProgress);
        statusCounts.put("completed", completed);
        statusCounts.put("signedOff", signedOff);
        statusCounts.put("withdrawn", withdrawn);
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
        verdictDistribution.put("WITHDRAWN", 0L);
        verdictDistribution.put("MISMATCH_WITH_JD", 0L);
        verdictDistribution.put("NEEDS_RESKILLING", 0L);
        verdictDistribution.put("NEEDS_1_WEEK_PREP", 0L);
        verdictDistribution.put("READY", 0L);
        
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
            dayTrend.put("label", date.format(DateTimeFormatter.ofPattern("EEE M/d")));
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
            weekTrend.put("label", "Wk " + weekStart.format(DateTimeFormatter.ofPattern("M/d")));
            weekTrend.put("interviews", weekInterviews);
            weekTrend.put("completed", weekCompleted);
            weekTrend.put("successRate", Math.round(successRate * 100.0) / 100.0);
            
            weeklyTrends.add(weekTrend);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("dailyTrends", dailyTrends);
        result.put("weeklyTrends", weeklyTrends);
        result.put("marketTrends", buildMarketTrends(userRole));
        result.put("hasData", dailyTrends.stream().anyMatch(d -> ((Number) d.get("interviews")).longValue() > 0)
                || weeklyTrends.stream().anyMatch(w -> ((Number) w.get("interviews")).longValue() > 0));
        result.put("generatedAt", Instant.now().toString());
        return result;
    }

    private Map<String, Object> buildMarketTrends(String userRole) {
        String allowedBranch = BranchAccess.resolveAllowedBranch(userRole);
        List<Client> activeClients = clientRepository.findByStatus(Client.ClientStatus.ACTIVE);
        if (allowedBranch != null) {
            activeClients = activeClients.stream()
                    .filter(c -> allowedBranch.equals(Branch.normalize(c.getBranch())))
                    .collect(Collectors.toList());
        }

        Map<String, Integer> skillBench = new HashMap<>();
        Map<String, Integer> skillMarket = new HashMap<>();
        Map<String, Integer> skillClientCounts = new HashMap<>();
        Map<String, Integer> roleCounts = new HashMap<>();

        int benchDemand = 0;
        int marketDemand = 0;

        for (Client client : activeClients) {
            int clientBenchCap = client.getBenchB2bCandidatesNeeded() != null ? client.getBenchB2bCandidatesNeeded() : 0;
            int clientMarketCap = client.getMarketCandidatesNeeded() != null ? client.getMarketCandidatesNeeded() : 0;

            String role = client.getJdRole() != null ? client.getJdRole() : "Unknown";
            roleCounts.merge(role, 1, Integer::sum);

            if (client.getSkillRequirements() != null && !client.getSkillRequirements().isEmpty()) {
                Map<String, Integer> clientSkillBench = new HashMap<>();
                Map<String, Integer> clientSkillMarket = new HashMap<>();

                for (SkillRequirement sr : client.getSkillRequirements()) {
                    String skill = normalizeSkillCode(sr.getSkillSet());
                    for (PositionRequirement pos : sr.getPositions()) {
                        int needed = pos.getCandidatesNeeded() != null ? pos.getCandidatesNeeded() : 0;
                        if ("MARKET".equalsIgnoreCase(pos.getSource())) {
                            clientSkillMarket.merge(skill, needed, Integer::sum);
                        } else {
                            clientSkillBench.merge(skill, needed, Integer::sum);
                        }
                    }
                }

                int rawBench = clientSkillBench.values().stream().mapToInt(Integer::intValue).sum();
                int rawMarket = clientSkillMarket.values().stream().mapToInt(Integer::intValue).sum();

                double benchScale = scaleFactor(rawBench, clientBenchCap);
                double marketScale = scaleFactor(rawMarket, clientMarketCap);

                int reconciledBench = 0;
                int reconciledMarket = 0;

                Set<String> clientSkills = new HashSet<>();
                clientSkills.addAll(clientSkillBench.keySet());
                clientSkills.addAll(clientSkillMarket.keySet());

                for (String skill : clientSkills) {
                    int bench = scaled(clientSkillBench.getOrDefault(skill, 0), benchScale);
                    int market = scaled(clientSkillMarket.getOrDefault(skill, 0), marketScale);
                    if (bench > 0 || market > 0) {
                        skillBench.merge(skill, bench, Integer::sum);
                        skillMarket.merge(skill, market, Integer::sum);
                        skillClientCounts.merge(skill, 1, Integer::sum);
                    }
                    reconciledBench += bench;
                    reconciledMarket += market;
                }

                benchDemand += reconciledBench > 0 ? reconciledBench : clientBenchCap;
                marketDemand += reconciledMarket > 0 ? reconciledMarket : clientMarketCap;
            } else {
                benchDemand += clientBenchCap;
                marketDemand += clientMarketCap;
            }
        }

        Set<String> allSkills = new HashSet<>();
        allSkills.addAll(skillBench.keySet());
        allSkills.addAll(skillMarket.keySet());

        List<Map<String, Object>> topSkills = allSkills.stream()
                .map(skill -> {
                    int bench = skillBench.getOrDefault(skill, 0);
                    int market = skillMarket.getOrDefault(skill, 0);
                    Map<String, Object> row = new HashMap<>();
                    row.put("skill", skill);
                    row.put("positionsNeeded", bench + market);
                    row.put("benchNeeded", bench);
                    row.put("marketNeeded", market);
                    row.put("clientCount", skillClientCounts.getOrDefault(skill, 0));
                    return row;
                })
                .sorted((a, b) -> Integer.compare(
                        (Integer) b.get("positionsNeeded"),
                        (Integer) a.get("positionsNeeded")))
                .limit(8)
                .collect(Collectors.toList());

        List<Map<String, Object>> topRoles = roleCounts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(6)
                .map(entry -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("role", entry.getKey());
                    row.put("count", entry.getValue());
                    return row;
                })
                .collect(Collectors.toList());

        Map<String, Object> market = new HashMap<>();
        market.put("period", "Current open demand from active clients (reconciled to client totals)");
        market.put("activeClients", activeClients.size());
        market.put("benchDemand", benchDemand);
        market.put("marketDemand", marketDemand);
        market.put("topSkills", topSkills);
        market.put("topRoles", topRoles);
        market.put("hasData", !topSkills.isEmpty() || benchDemand > 0 || marketDemand > 0);
        return market;
    }

    /** Scale skill-level position counts down when they exceed the client-level cap. */
    private static double scaleFactor(int rawSkillTotal, int clientCap) {
        if (rawSkillTotal <= 0) {
            return 0;
        }
        if (clientCap <= 0) {
            return 1.0;
        }
        return Math.min(1.0, (double) clientCap / rawSkillTotal);
    }

    private static int scaled(int raw, double scale) {
        if (raw <= 0 || scale <= 0) {
            return 0;
        }
        return Math.max(0, (int) Math.round(raw * scale));
    }

    private static String normalizeSkillCode(String skill) {
        if (skill == null || skill.isBlank()) {
            return "UNKNOWN";
        }
        return skill.trim().toUpperCase(Locale.ROOT);
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

    private List<Interview> getUserInterviews(String userId, String userRole) {
        List<Interview> all = interviewRepository.findAll();
        return InterviewBranchFilter.filterForRole(all, userId, userRole, candidateBranchLookup.forInterviews(all));
    }

    /** Branch-scoped daily report for digest (null branch = all branches). */
    public Map<String, Object> getDailyReportDataForBranch(String branch) {
        String normalizedBranch = branch != null && !branch.isBlank() ? Branch.normalize(branch) : null;
        Instant startOfDay = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endOfDay = startOfDay.plusSeconds(24 * 3600);

        List<Interview> allInterviews = interviewRepository.findAll().stream()
                .filter(i -> normalizedBranch == null || normalizedBranch.equals(Branch.normalize(i.getBranch())))
                .collect(Collectors.toList());
        List<Interview> todayInterviews = allInterviews.stream()
                .filter(i -> i.getCreatedAt().isAfter(startOfDay) && i.getCreatedAt().isBefore(endOfDay))
                .collect(Collectors.toList());

        return buildDailyReport(allInterviews, todayInterviews, normalizedBranch);
    }

    public Map<String, Object> getDailyReportData(String userId, String userRole) {
        List<Interview> scopedInterviews = getUserInterviews(userId, userRole);
        Instant startOfDay = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endOfDay = startOfDay.plusSeconds(24 * 3600);
        List<Interview> todayInterviews = scopedInterviews.stream()
                .filter(i -> i.getCreatedAt().isAfter(startOfDay) && i.getCreatedAt().isBefore(endOfDay))
                .collect(Collectors.toList());

        String branch = BranchAccess.resolveAllowedBranch(userRole);
        return buildDailyReport(scopedInterviews, todayInterviews, branch);
    }

    private Map<String, Object> buildDailyReport(List<Interview> scopedInterviews,
                                                  List<Interview> todayInterviews,
                                                  String branchScope) {
        Map<String, Object> report = new HashMap<>();

        Map<String, Object> interviewMetrics = new HashMap<>();
        interviewMetrics.put("totalToday", todayInterviews.size());
        interviewMetrics.put("scheduled", todayInterviews.stream().filter(i -> i.getStatus() == InterviewStatus.SCHEDULED).count());
        interviewMetrics.put("inProgress", todayInterviews.stream().filter(i -> i.getStatus() == InterviewStatus.IN_PROGRESS).count());
        interviewMetrics.put("completed", todayInterviews.stream().filter(i -> i.getStatus() == InterviewStatus.COMPLETED).count());
        interviewMetrics.put("reviewPending", todayInterviews.stream().filter(i -> i.getStatus() == InterviewStatus.REVIEW_PENDING).count());
        interviewMetrics.put("signedOff", todayInterviews.stream().filter(i -> i.getStatus() == InterviewStatus.SIGNED_OFF).count());

        Map<String, Long> modeDistribution = todayInterviews.stream()
                .collect(Collectors.groupingBy(
                    i -> i.getInterviewMode() != null ? i.getInterviewMode().name() : "UNKNOWN",
                    Collectors.counting()
                ));
        interviewMetrics.put("modeDistribution", modeDistribution);

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

        Map<String, Object> violations = new HashMap<>();
        long withdrawnCount = todayInterviews.stream()
                .filter(i -> i.getProposedVerdict() == ReadinessVerdict.WITHDRAWN || i.getFinalVerdict() == ReadinessVerdict.WITHDRAWN)
                .count();
        violations.put("totalWithdrawn", withdrawnCount);

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

        Instant oneDayAgo = Instant.now().minusSeconds(24 * 3600);
        List<Map<String, Object>> pendingReviews = scopedInterviews.stream()
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
        if (branchScope != null) {
            report.put("branch", branchScope);
        }
        return report;
    }

    public Map<String, Object> getCandidatePerformanceAnalytics(String userId, String userRole) {
        List<Interview> interviews = getUserInterviews(userId, userRole);

        List<Interview> assessedInterviews = interviews.stream()
                .filter(i -> i.getStatus() == InterviewStatus.COMPLETED || i.getStatus() == InterviewStatus.SIGNED_OFF)
                .filter(i -> i.getFinalVerdict() != null)
                .collect(Collectors.toList());
        
        // Candidate performance by verdict
        Map<String, Long> performanceByVerdict = new LinkedHashMap<>();
        performanceByVerdict.put("WITHDRAWN", 0L);
        performanceByVerdict.put("MISMATCH_WITH_JD", 0L);
        performanceByVerdict.put("NEEDS_RESKILLING", 0L);
        performanceByVerdict.put("NEEDS_1_WEEK_PREP", 0L);
        performanceByVerdict.put("READY", 0L);
        
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
        
        // Per-interview records, then group by candidate email for the dashboard
        List<Map<String, Object>> interviewRecords = assessedInterviews.stream()
                .map(this::buildCandidateInterviewRecord)
                .sorted((a, b) -> Double.compare(
                        (Double) b.get("averageScore"),
                        (Double) a.get("averageScore")))
                .collect(Collectors.toList());

        Map<String, List<Map<String, Object>>> interviewsByEmail = interviewRecords.stream()
                .collect(Collectors.groupingBy(r -> (String) r.get("candidateEmail")));

        List<Map<String, Object>> candidateSummaries = interviewsByEmail.entrySet().stream()
                .map(e -> buildCandidateSummary(e.getValue()))
                .sorted((a, b) -> Double.compare(
                        (Double) b.get("bestAverageScore"),
                        (Double) a.get("bestAverageScore")))
                .limit(15)
                .collect(Collectors.toList());

        // Legacy flat list (one row per interview) — kept for compatibility
        List<Map<String, Object>> topCandidates = interviewRecords.stream()
                .limit(10)
                .collect(Collectors.toList());
        
        // Skill gap analysis — unique candidates per weak dimension (normalized keys)
        Map<String, Map<String, Map<String, Object>>> skillGapCandidates = new LinkedHashMap<>();
        Map<String, String> skillGapDisplayNames = new LinkedHashMap<>();
        int uniqueCandidateCount = (int) interviewsByEmail.keySet().stream()
                .filter(email -> email != null && !email.isBlank())
                .count();

        assessedInterviews.forEach(interview -> {
            Engineer engineer = engineerRepository.findById(interview.getEngineerId()).orElse(null);
            String candidateEmail = engineer != null ? engineer.getEmail() : "";
            String candidateName = engineer != null && engineer.getName() != null && !engineer.getName().isBlank()
                    ? engineer.getName()
                    : candidateEmail;
            if (candidateEmail == null || candidateEmail.isBlank()) {
                return;
            }

            List<Map<String, Object>> scores;
            try {
                scores = reviewServiceClient.getScores(interview.getId());
            } catch (Exception e) {
                scores = List.of();
            }
            scores.stream()
                    .filter(score -> (Integer) score.get("value") < 3)
                    .forEach(score -> {
                        String dimension = (String) score.get("dimension");
                        String normalizedKey = normalizeDimensionKey(dimension);
                        if (normalizedKey.isEmpty()) {
                            return;
                        }
                        skillGapDisplayNames.merge(normalizedKey, dimension, this::preferDimensionLabel);
                        skillGapCandidates
                                .computeIfAbsent(normalizedKey, k -> new LinkedHashMap<>())
                                .putIfAbsent(candidateEmail, Map.of(
                                        "candidateName", candidateName,
                                        "candidateEmail", candidateEmail
                                ));
                    });
        });
        
        List<Map<String, Object>> commonWeaknesses = skillGapCandidates.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                .limit(8)
                .map(entry -> {
                    Map<String, Object> weakness = new HashMap<>();
                    int count = entry.getValue().size();
                    String displaySkill = formatDimensionLabel(
                            skillGapDisplayNames.getOrDefault(entry.getKey(), entry.getKey()));
                    weakness.put("skill", displaySkill);
                    weakness.put("candidateCount", count);
                    weakness.put("percentage", uniqueCandidateCount > 0
                            ? Math.round((double) count / uniqueCandidateCount * 100 * 100.0) / 100.0
                            : 0);
                    weakness.put("candidates", new ArrayList<>(entry.getValue().values()));
                    return weakness;
                })
                .collect(Collectors.toList());
        
        // Average scores by skill dimension (normalized keys)
        Map<String, Double> avgScoresByDimension = new HashMap<>();
        Map<String, Integer> dimensionCounts = new HashMap<>();
        Map<String, String> avgScoreDisplayNames = new LinkedHashMap<>();
        
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
                String normalizedKey = normalizeDimensionKey(dimension);
                if (normalizedKey.isEmpty()) {
                    return;
                }
                Integer value = (Integer) score.get("value");
                avgScoreDisplayNames.merge(normalizedKey, dimension, this::preferDimensionLabel);
                avgScoresByDimension.put(normalizedKey, avgScoresByDimension.getOrDefault(normalizedKey, 0.0) + value);
                dimensionCounts.put(normalizedKey, dimensionCounts.getOrDefault(normalizedKey, 0) + 1);
            });
        });
        
        Map<String, Double> finalAvgScores = avgScoresByDimension.entrySet().stream()
                .collect(Collectors.toMap(
                    entry -> formatDimensionLabel(avgScoreDisplayNames.getOrDefault(entry.getKey(), entry.getKey())),
                    entry -> Math.round(entry.getValue() / dimensionCounts.get(entry.getKey()) * 100.0) / 100.0,
                    (a, b) -> a,
                    LinkedHashMap::new
                ));
        
        Map<String, Object> result = new HashMap<>();
        result.put("performanceByVerdict", performanceByVerdict);
        result.put("performanceByMode", performanceByMode);
        result.put("topCandidates", topCandidates);
        result.put("candidateSummaries", candidateSummaries);
        result.put("commonWeaknesses", commonWeaknesses);
        result.put("averageScoresBySkill", finalAvgScores);
        result.put("totalAssessedCandidates", assessedInterviews.size());
        result.put("uniqueCandidates", uniqueCandidateCount);
        result.put("overallSuccessRate", assessedInterviews.size() > 0 ? 
            Math.round((double) performanceByVerdict.get("READY") / assessedInterviews.size() * 100 * 100.0) / 100.0 : 0);
        result.put("hasData", !assessedInterviews.isEmpty());
        
        return result;
    }

    private Map<String, Object> buildCandidateInterviewRecord(Interview interview) {
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
            scores = List.of();
        }

        double avgScore = scores.stream()
                .mapToInt(score -> (Integer) score.get("value"))
                .average()
                .orElse(0.0);

        Map<String, Object> record = new HashMap<>();
        record.put("interviewId", interview.getId().toString());
        record.put("candidateName", candidateName);
        record.put("candidateEmail", candidateEmail);
        record.put("interviewMode", interview.getInterviewMode().name());
        record.put("averageScore", Math.round(avgScore * 100.0) / 100.0);
        record.put("verdict", interview.getFinalVerdict().name());
        record.put("jdTitle", jdTitle);
        record.put("interviewDate", interview.getCreatedAt().toString().substring(0, 10));
        record.put("scores", scores.stream()
                .map(score -> Map.of(
                        "dimension", score.get("dimension"),
                        "value", score.get("value")))
                .collect(Collectors.toList()));
        return record;
    }

    private Map<String, Object> buildCandidateSummary(List<Map<String, Object>> interviews) {
        List<Map<String, Object>> sorted = interviews.stream()
                .sorted((a, b) -> ((String) b.get("interviewDate")).compareTo((String) a.get("interviewDate")))
                .collect(Collectors.toList());

        Map<String, Object> first = sorted.get(0);
        String candidateName = (String) first.get("candidateName");
        String candidateEmail = (String) first.get("candidateEmail");

        Map<String, Integer> roundCounts = new LinkedHashMap<>();
        for (Map<String, Object> interview : sorted) {
            String mode = (String) interview.get("interviewMode");
            roundCounts.merge(mode, 1, Integer::sum);
        }

        double bestAverageScore = sorted.stream()
                .mapToDouble(i -> (Double) i.get("averageScore"))
                .max()
                .orElse(0.0);
        double overallAverageScore = Math.round(sorted.stream()
                .mapToDouble(i -> (Double) i.get("averageScore"))
                .average()
                .orElse(0.0) * 100.0) / 100.0;

        Map<String, Object> summary = new HashMap<>();
        summary.put("candidateName", candidateName);
        summary.put("candidateEmail", candidateEmail);
        summary.put("interviewCount", sorted.size());
        summary.put("roundCounts", roundCounts);
        summary.put("bestAverageScore", Math.round(bestAverageScore * 100.0) / 100.0);
        summary.put("overallAverageScore", overallAverageScore);
        summary.put("latestVerdict", first.get("verdict"));
        summary.put("latestJdTitle", first.get("jdTitle"));
        summary.put("interviews", sorted);
        return summary;
    }

    /** Collapse casing/spacing variants such as "communication" vs "Communication". */
    private String normalizeDimensionKey(String dimension) {
        if (dimension == null || dimension.isBlank()) {
            return "";
        }
        return dimension.replaceAll("[\\s_-]+", "").toLowerCase(Locale.ROOT);
    }

    private String preferDimensionLabel(String existing, String incoming) {
        if (existing == null || existing.isBlank()) {
            return incoming;
        }
        if (incoming == null || incoming.isBlank()) {
            return existing;
        }
        boolean existingCamel = Character.isLowerCase(existing.charAt(0));
        boolean incomingCamel = Character.isLowerCase(incoming.charAt(0));
        if (existingCamel && !incomingCamel) {
            return existing;
        }
        if (incomingCamel && !existingCamel) {
            return incoming;
        }
        return existing.length() >= incoming.length() ? existing : incoming;
    }

    private String formatDimensionLabel(String dimension) {
        if (dimension == null || dimension.isBlank()) {
            return "";
        }
        String spaced = dimension
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .replace('_', ' ')
                .trim();
        if (spaced.isEmpty()) {
            return dimension;
        }
        String[] words = spaced.split("\\s+");
        StringBuilder label = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (word.isEmpty()) {
                continue;
            }
            if (i > 0) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                label.append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return label.toString();
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