package com.benchreadiness.interview.service;

import com.benchreadiness.interview.client.AuthServiceClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class BulkResumeProcessingService {
    
    private final AuthServiceClient authServiceClient;
    private final ResumeParsingService parsingService;
    private final ResumeSummaryService summaryService;
    
    public BulkResumeProcessingService(AuthServiceClient authServiceClient,
                                     ResumeParsingService parsingService,
                                     ResumeSummaryService summaryService) {
        this.authServiceClient = authServiceClient;
        this.parsingService = parsingService;
        this.summaryService = summaryService;
    }
    
    public BulkProcessingResult processCandidatesWithoutSummaries(String adminUserId, String adminRole) {
        BulkProcessingResult result = new BulkProcessingResult();
        
        try {
            // Get all candidates
            List<Map<String, Object>> candidates = authServiceClient.searchCandidates("");
            
            List<String> candidatesNeedingProcessing = new ArrayList<>();
            List<String> candidatesWithResumes = new ArrayList<>();
            List<String> candidatesWithoutResumes = new ArrayList<>();
            
            // Filter candidates that need processing
            for (Map<String, Object> candidate : candidates) {
                String candidateId = (String) candidate.get("id");
                String resumeSummary = (String) candidate.get("resumeSummary");
                String resumeFilePath = (String) candidate.get("resumeFilePath");
                
                if (resumeFilePath != null && !resumeFilePath.trim().isEmpty()) {
                    candidatesWithResumes.add(candidateId);
                    
                    // Check if summary needs processing
                    if (resumeSummary == null || resumeSummary.trim().isEmpty() || 
                        resumeSummary.contains("[Add specific project experience")) {
                        candidatesNeedingProcessing.add(candidateId);
                    }
                } else {
                    candidatesWithoutResumes.add(candidateId);
                }
            }
            
            result.setTotalCandidates(candidates.size());
            result.setCandidatesWithResumes(candidatesWithResumes.size());
            result.setCandidatesWithoutResumes(candidatesWithoutResumes.size());
            result.setCandidatesNeedingProcessing(candidatesNeedingProcessing.size());
            
            // Process candidates asynchronously
            if (!candidatesNeedingProcessing.isEmpty()) {
                processResumesBatch(candidatesNeedingProcessing, result);
            }
            
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage("Failed to process candidates: " + e.getMessage());
        }
        
        return result;
    }
    
    @Async
    public CompletableFuture<Void> processResumesBatch(List<String> candidateIds, BulkProcessingResult result) {
        List<String> processed = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        
        for (String candidateId : candidateIds) {
            try {
                boolean success = processSingleCandidateResume(candidateId, "system", "ADMIN");
                if (success) {
                    processed.add(candidateId);
                } else {
                    failed.add(candidateId);
                }
            } catch (Exception e) {
                System.err.println("Failed to process resume for candidate " + candidateId + ": " + e.getMessage());
                failed.add(candidateId);
            }
        }
        
        result.setProcessedSuccessfully(processed.size());
        result.setProcessedWithErrors(failed.size());
        result.setProcessedCandidateIds(processed);
        result.setFailedCandidateIds(failed);
        result.setCompleted(true);
        
        return CompletableFuture.completedFuture(null);
    }
    
    private boolean processSingleCandidateResume(String candidateId, String adminUserId, String adminRole) {
        try {
            // Get candidate details
            Map<String, Object> candidate = authServiceClient.getUserById(candidateId);
            if (candidate == null) {
                return false;
            }
            
            String resumeFilePath = (String) candidate.get("resumeFilePath");
            String candidateName = (String) candidate.get("name");
            
            if (resumeFilePath == null || resumeFilePath.trim().isEmpty()) {
                return false;
            }
            
            // Extract text from existing resume file
            String extractedText;
            try {
                extractedText = parsingService.extractTextFromFile(resumeFilePath);
            } catch (Exception e) {
                System.err.println("Failed to extract text from resume for candidate " + candidateId + ": " + e.getMessage());
                return false;
            }
            
            // Generate AI summary
            ResumeSummaryService.ResumeSummaryResult summaryResult = 
                summaryService.processResumeSummary(extractedText, candidateName);
            
            // Update candidate profile
            Map<String, Object> updateRequest = new HashMap<>();
            updateRequest.put("resumeParsedText", extractedText);
            updateRequest.put("resumeSummary", summaryResult.getSummary());
            updateRequest.put("resumeUpdatedAt", java.time.Instant.now().toString());
            
            authServiceClient.updateCandidateResume(candidateId, updateRequest);
            
            return true;
            
        } catch (Exception e) {
            System.err.println("Failed to process candidate " + candidateId + ": " + e.getMessage());
            return false;
        }
    }
    
    public ResumeProcessingStats getProcessingStats(String adminUserId, String adminRole) {
        try {
            List<Map<String, Object>> candidates = authServiceClient.searchCandidates("");
            
            ResumeProcessingStats stats = new ResumeProcessingStats();
            stats.setTotalCandidates(candidates.size());
            
            int withResumes = 0;
            int withSummaries = 0;
            int needsProcessing = 0;
            int withAiSummaries = 0;
            
            for (Map<String, Object> candidate : candidates) {
                String resumeFilePath = (String) candidate.get("resumeFilePath");
                String resumeSummary = (String) candidate.get("resumeSummary");
                
                if (resumeFilePath != null && !resumeFilePath.trim().isEmpty()) {
                    withResumes++;
                    
                    if (resumeSummary != null && !resumeSummary.trim().isEmpty()) {
                        withSummaries++;
                        
                        // Check if it's an AI-generated summary (doesn't contain placeholder text)
                        if (!resumeSummary.contains("[Add specific project experience")) {
                            withAiSummaries++;
                        }
                    } else {
                        needsProcessing++;
                    }
                }
            }
            
            stats.setCandidatesWithResumes(withResumes);
            stats.setCandidatesWithSummaries(withSummaries);
            stats.setCandidatesNeedingProcessing(needsProcessing);
            stats.setCandidatesWithAiSummaries(withAiSummaries);
            stats.setProcessingCompletionRate(withResumes > 0 ? (double) withAiSummaries / withResumes * 100 : 0);
            
            return stats;
            
        } catch (Exception e) {
            ResumeProcessingStats errorStats = new ResumeProcessingStats();
            errorStats.setErrorMessage("Failed to get processing stats: " + e.getMessage());
            return errorStats;
        }
    }
    
    public static class BulkProcessingResult {
        private boolean success = true;
        private String errorMessage;
        private int totalCandidates;
        private int candidatesWithResumes;
        private int candidatesWithoutResumes;
        private int candidatesNeedingProcessing;
        private int processedSuccessfully;
        private int processedWithErrors;
        private List<String> processedCandidateIds = new ArrayList<>();
        private List<String> failedCandidateIds = new ArrayList<>();
        private boolean completed = false;
        
        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public int getTotalCandidates() { return totalCandidates; }
        public void setTotalCandidates(int totalCandidates) { this.totalCandidates = totalCandidates; }
        public int getCandidatesWithResumes() { return candidatesWithResumes; }
        public void setCandidatesWithResumes(int candidatesWithResumes) { this.candidatesWithResumes = candidatesWithResumes; }
        public int getCandidatesWithoutResumes() { return candidatesWithoutResumes; }
        public void setCandidatesWithoutResumes(int candidatesWithoutResumes) { this.candidatesWithoutResumes = candidatesWithoutResumes; }
        public int getCandidatesNeedingProcessing() { return candidatesNeedingProcessing; }
        public void setCandidatesNeedingProcessing(int candidatesNeedingProcessing) { this.candidatesNeedingProcessing = candidatesNeedingProcessing; }
        public int getProcessedSuccessfully() { return processedSuccessfully; }
        public void setProcessedSuccessfully(int processedSuccessfully) { this.processedSuccessfully = processedSuccessfully; }
        public int getProcessedWithErrors() { return processedWithErrors; }
        public void setProcessedWithErrors(int processedWithErrors) { this.processedWithErrors = processedWithErrors; }
        public List<String> getProcessedCandidateIds() { return processedCandidateIds; }
        public void setProcessedCandidateIds(List<String> processedCandidateIds) { this.processedCandidateIds = processedCandidateIds; }
        public List<String> getFailedCandidateIds() { return failedCandidateIds; }
        public void setFailedCandidateIds(List<String> failedCandidateIds) { this.failedCandidateIds = failedCandidateIds; }
        public boolean isCompleted() { return completed; }
        public void setCompleted(boolean completed) { this.completed = completed; }
    }
    
    public static class ResumeProcessingStats {
        private int totalCandidates;
        private int candidatesWithResumes;
        private int candidatesWithSummaries;
        private int candidatesNeedingProcessing;
        private int candidatesWithAiSummaries;
        private double processingCompletionRate;
        private String errorMessage;
        
        // Getters and setters
        public int getTotalCandidates() { return totalCandidates; }
        public void setTotalCandidates(int totalCandidates) { this.totalCandidates = totalCandidates; }
        public int getCandidatesWithResumes() { return candidatesWithResumes; }
        public void setCandidatesWithResumes(int candidatesWithResumes) { this.candidatesWithResumes = candidatesWithResumes; }
        public int getCandidatesWithSummaries() { return candidatesWithSummaries; }
        public void setCandidatesWithSummaries(int candidatesWithSummaries) { this.candidatesWithSummaries = candidatesWithSummaries; }
        public int getCandidatesNeedingProcessing() { return candidatesNeedingProcessing; }
        public void setCandidatesNeedingProcessing(int candidatesNeedingProcessing) { this.candidatesNeedingProcessing = candidatesNeedingProcessing; }
        public int getCandidatesWithAiSummaries() { return candidatesWithAiSummaries; }
        public void setCandidatesWithAiSummaries(int candidatesWithAiSummaries) { this.candidatesWithAiSummaries = candidatesWithAiSummaries; }
        public double getProcessingCompletionRate() { return processingCompletionRate; }
        public void setProcessingCompletionRate(double processingCompletionRate) { this.processingCompletionRate = processingCompletionRate; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
}