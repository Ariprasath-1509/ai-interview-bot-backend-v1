package com.benchreadiness.interview.service;

import com.benchreadiness.interview.client.AuthServiceClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ResumeAnalyticsService {
    
    private final AuthServiceClient authServiceClient;
    
    public ResumeAnalyticsService(AuthServiceClient authServiceClient) {
        this.authServiceClient = authServiceClient;
    }
    
    public ResumeAnalytics getResumeAnalytics(String adminUserId, String adminRole) {
        try {
            List<Map<String, Object>> candidates = authServiceClient.searchCandidates("");
            
            ResumeAnalytics analytics = new ResumeAnalytics();
            analytics.setTotalCandidates(candidates.size());
            
            // Initialize counters
            int withResumes = 0;
            int withSummaries = 0;
            int withAiSummaries = 0;
            int uploadedToday = 0;
            int uploadedThisWeek = 0;
            int uploadedThisMonth = 0;
            
            Map<String, Integer> uploadsByDate = new HashMap<>();
            Map<String, Integer> uploadsBySkillSet = new HashMap<>();
            Map<String, Integer> uploadsBySource = new HashMap<>();
            List<String> recentUploads = new ArrayList<>();
            
            LocalDate today = LocalDate.now();
            LocalDate weekAgo = today.minusDays(7);
            LocalDate monthAgo = today.minusDays(30);
            
            for (Map<String, Object> candidate : candidates) {
                String candidateId = (String) candidate.get("id");
                String candidateName = (String) candidate.get("name");
                String resumeFilePath = (String) candidate.get("resumeFilePath");
                String resumeSummary = (String) candidate.get("resumeSummary");
                String resumeUploadedAt = (String) candidate.get("resumeUploadedAt");
                String skillSet = (String) candidate.get("skillSet");
                String source = (String) candidate.get("source");
                
                // Count resumes
                if (resumeFilePath != null && !resumeFilePath.trim().isEmpty()) {
                    withResumes++;
                    
                    // Count summaries
                    if (resumeSummary != null && !resumeSummary.trim().isEmpty()) {
                        withSummaries++;
                        
                        // Check if AI-generated
                        if (!resumeSummary.contains("[Add specific project experience")) {
                            withAiSummaries++;
                        }
                    }
                    
                    // Analyze upload dates
                    if (resumeUploadedAt != null) {
                        try {
                            Instant uploadInstant = Instant.parse(resumeUploadedAt);
                            LocalDate uploadDate = uploadInstant.atZone(ZoneId.systemDefault()).toLocalDate();
                            
                            // Count by time periods
                            if (uploadDate.equals(today)) {
                                uploadedToday++;
                            }
                            if (uploadDate.isAfter(weekAgo) || uploadDate.equals(weekAgo)) {
                                uploadedThisWeek++;
                            }
                            if (uploadDate.isAfter(monthAgo) || uploadDate.equals(monthAgo)) {
                                uploadedThisMonth++;
                            }
                            
                            // Group by date for trends
                            String dateKey = uploadDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
                            uploadsByDate.merge(dateKey, 1, Integer::sum);
                            
                            // Recent uploads (last 7 days)
                            if (uploadDate.isAfter(weekAgo)) {
                                recentUploads.add(candidateName + " (" + dateKey + ")");
                            }
                            
                        } catch (Exception e) {
                            // Skip invalid dates
                        }
                    }
                    
                    // Group by skill set
                    if (skillSet != null) {
                        uploadsBySkillSet.merge(skillSet, 1, Integer::sum);
                    }
                    
                    // Group by source
                    if (source != null) {
                        uploadsBySource.merge(source, 1, Integer::sum);
                    }
                }
            }
            
            // Set basic metrics
            analytics.setCandidatesWithResumes(withResumes);
            analytics.setCandidatesWithSummaries(withSummaries);
            analytics.setCandidatesWithAiSummaries(withAiSummaries);
            analytics.setUploadedToday(uploadedToday);
            analytics.setUploadedThisWeek(uploadedThisWeek);
            analytics.setUploadedThisMonth(uploadedThisMonth);
            
            // Calculate rates
            analytics.setResumeUploadRate(candidates.size() > 0 ? (double) withResumes / candidates.size() * 100 : 0);
            analytics.setSummaryGenerationRate(withResumes > 0 ? (double) withSummaries / withResumes * 100 : 0);
            analytics.setAiProcessingRate(withResumes > 0 ? (double) withAiSummaries / withResumes * 100 : 0);
            
            // Set distribution data
            analytics.setUploadsByDate(uploadsByDate);
            analytics.setUploadsBySkillSet(uploadsBySkillSet);
            analytics.setUploadsBySource(uploadsBySource);
            analytics.setRecentUploads(recentUploads.stream().limit(10).collect(Collectors.toList()));
            
            // Generate insights
            analytics.setInsights(generateInsights(analytics));
            
            return analytics;
            
        } catch (Exception e) {
            ResumeAnalytics errorAnalytics = new ResumeAnalytics();
            errorAnalytics.setErrorMessage("Failed to generate resume analytics: " + e.getMessage());
            return errorAnalytics;
        }
    }
    
    public UploadTrends getUploadTrends(String adminUserId, String adminRole, int days) {
        try {
            List<Map<String, Object>> candidates = authServiceClient.searchCandidates("");
            
            UploadTrends trends = new UploadTrends();
            trends.setDaysAnalyzed(days);
            
            Map<String, Integer> dailyUploads = new HashMap<>();
            LocalDate startDate = LocalDate.now().minusDays(days);
            
            // Initialize all dates with 0
            for (int i = 0; i <= days; i++) {
                LocalDate date = startDate.plusDays(i);
                dailyUploads.put(date.format(DateTimeFormatter.ISO_LOCAL_DATE), 0);
            }
            
            // Count actual uploads
            for (Map<String, Object> candidate : candidates) {
                String resumeUploadedAt = (String) candidate.get("resumeUploadedAt");
                
                if (resumeUploadedAt != null) {
                    try {
                        Instant uploadInstant = Instant.parse(resumeUploadedAt);
                        LocalDate uploadDate = uploadInstant.atZone(ZoneId.systemDefault()).toLocalDate();
                        
                        if (uploadDate.isAfter(startDate.minusDays(1))) {
                            String dateKey = uploadDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
                            dailyUploads.merge(dateKey, 1, Integer::sum);
                        }
                    } catch (Exception e) {
                        // Skip invalid dates
                    }
                }
            }
            
            trends.setDailyUploads(dailyUploads);
            
            // Calculate trend metrics
            List<Integer> values = new ArrayList<>(dailyUploads.values());
            if (!values.isEmpty()) {
                trends.setAverageUploadsPerDay(values.stream().mapToInt(Integer::intValue).average().orElse(0));
                trends.setPeakUploadDay(Collections.max(values));
                trends.setTotalUploadsInPeriod(values.stream().mapToInt(Integer::intValue).sum());
            }
            
            return trends;
            
        } catch (Exception e) {
            UploadTrends errorTrends = new UploadTrends();
            errorTrends.setErrorMessage("Failed to generate upload trends: " + e.getMessage());
            return errorTrends;
        }
    }
    
    private List<String> generateInsights(ResumeAnalytics analytics) {
        List<String> insights = new ArrayList<>();
        
        // Upload rate insights
        if (analytics.getResumeUploadRate() < 30) {
            insights.add("Low resume upload rate (" + String.format("%.1f", analytics.getResumeUploadRate()) + "%) - consider encouraging candidates to upload resumes");
        } else if (analytics.getResumeUploadRate() > 80) {
            insights.add("Excellent resume upload rate (" + String.format("%.1f", analytics.getResumeUploadRate()) + "%) - candidates are actively engaged");
        }
        
        // AI processing insights
        if (analytics.getAiProcessingRate() < 50) {
            insights.add("Many resumes need AI processing (" + String.format("%.1f", analytics.getAiProcessingRate()) + "% processed) - consider running bulk processing");
        }
        
        // Recent activity insights
        if (analytics.getUploadedToday() > 5) {
            insights.add("High activity today with " + analytics.getUploadedToday() + " resume uploads");
        } else if (analytics.getUploadedThisWeek() == 0) {
            insights.add("No resume uploads this week - candidate engagement may be low");
        }
        
        // Skill set insights
        Map<String, Integer> skillUploads = analytics.getUploadsBySkillSet();
        if (!skillUploads.isEmpty()) {
            String topSkill = skillUploads.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Unknown");
            insights.add("Most active skill set: " + topSkill + " (" + skillUploads.get(topSkill) + " resumes)");
        }
        
        // Source insights
        Map<String, Integer> sourceUploads = analytics.getUploadsBySource();
        if (!sourceUploads.isEmpty()) {
            String topSource = sourceUploads.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Unknown");
            insights.add("Most active candidate source: " + topSource + " (" + sourceUploads.get(topSource) + " resumes)");
        }
        
        return insights;
    }
    
    public static class ResumeAnalytics {
        private int totalCandidates;
        private int candidatesWithResumes;
        private int candidatesWithSummaries;
        private int candidatesWithAiSummaries;
        private int uploadedToday;
        private int uploadedThisWeek;
        private int uploadedThisMonth;
        private double resumeUploadRate;
        private double summaryGenerationRate;
        private double aiProcessingRate;
        private Map<String, Integer> uploadsByDate;
        private Map<String, Integer> uploadsBySkillSet;
        private Map<String, Integer> uploadsBySource;
        private List<String> recentUploads;
        private List<String> insights;
        private String errorMessage;
        
        // Getters and setters
        public int getTotalCandidates() { return totalCandidates; }
        public void setTotalCandidates(int totalCandidates) { this.totalCandidates = totalCandidates; }
        public int getCandidatesWithResumes() { return candidatesWithResumes; }
        public void setCandidatesWithResumes(int candidatesWithResumes) { this.candidatesWithResumes = candidatesWithResumes; }
        public int getCandidatesWithSummaries() { return candidatesWithSummaries; }
        public void setCandidatesWithSummaries(int candidatesWithSummaries) { this.candidatesWithSummaries = candidatesWithSummaries; }
        public int getCandidatesWithAiSummaries() { return candidatesWithAiSummaries; }
        public void setCandidatesWithAiSummaries(int candidatesWithAiSummaries) { this.candidatesWithAiSummaries = candidatesWithAiSummaries; }
        public int getUploadedToday() { return uploadedToday; }
        public void setUploadedToday(int uploadedToday) { this.uploadedToday = uploadedToday; }
        public int getUploadedThisWeek() { return uploadedThisWeek; }
        public void setUploadedThisWeek(int uploadedThisWeek) { this.uploadedThisWeek = uploadedThisWeek; }
        public int getUploadedThisMonth() { return uploadedThisMonth; }
        public void setUploadedThisMonth(int uploadedThisMonth) { this.uploadedThisMonth = uploadedThisMonth; }
        public double getResumeUploadRate() { return resumeUploadRate; }
        public void setResumeUploadRate(double resumeUploadRate) { this.resumeUploadRate = resumeUploadRate; }
        public double getSummaryGenerationRate() { return summaryGenerationRate; }
        public void setSummaryGenerationRate(double summaryGenerationRate) { this.summaryGenerationRate = summaryGenerationRate; }
        public double getAiProcessingRate() { return aiProcessingRate; }
        public void setAiProcessingRate(double aiProcessingRate) { this.aiProcessingRate = aiProcessingRate; }
        public Map<String, Integer> getUploadsByDate() { return uploadsByDate; }
        public void setUploadsByDate(Map<String, Integer> uploadsByDate) { this.uploadsByDate = uploadsByDate; }
        public Map<String, Integer> getUploadsBySkillSet() { return uploadsBySkillSet; }
        public void setUploadsBySkillSet(Map<String, Integer> uploadsBySkillSet) { this.uploadsBySkillSet = uploadsBySkillSet; }
        public Map<String, Integer> getUploadsBySource() { return uploadsBySource; }
        public void setUploadsBySource(Map<String, Integer> uploadsBySource) { this.uploadsBySource = uploadsBySource; }
        public List<String> getRecentUploads() { return recentUploads; }
        public void setRecentUploads(List<String> recentUploads) { this.recentUploads = recentUploads; }
        public List<String> getInsights() { return insights; }
        public void setInsights(List<String> insights) { this.insights = insights; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
    
    public static class UploadTrends {
        private int daysAnalyzed;
        private Map<String, Integer> dailyUploads;
        private double averageUploadsPerDay;
        private int peakUploadDay;
        private int totalUploadsInPeriod;
        private String errorMessage;
        
        // Getters and setters
        public int getDaysAnalyzed() { return daysAnalyzed; }
        public void setDaysAnalyzed(int daysAnalyzed) { this.daysAnalyzed = daysAnalyzed; }
        public Map<String, Integer> getDailyUploads() { return dailyUploads; }
        public void setDailyUploads(Map<String, Integer> dailyUploads) { this.dailyUploads = dailyUploads; }
        public double getAverageUploadsPerDay() { return averageUploadsPerDay; }
        public void setAverageUploadsPerDay(double averageUploadsPerDay) { this.averageUploadsPerDay = averageUploadsPerDay; }
        public int getPeakUploadDay() { return peakUploadDay; }
        public void setPeakUploadDay(int peakUploadDay) { this.peakUploadDay = peakUploadDay; }
        public int getTotalUploadsInPeriod() { return totalUploadsInPeriod; }
        public void setTotalUploadsInPeriod(int totalUploadsInPeriod) { this.totalUploadsInPeriod = totalUploadsInPeriod; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
}