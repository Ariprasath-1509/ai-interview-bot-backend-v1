package com.benchreadiness.interview.service;

import com.benchreadiness.interview.client.AiServiceClient;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class ResumeSummaryService {
    
    private final AiServiceClient aiServiceClient;
    
    public ResumeSummaryService(AiServiceClient aiServiceClient) {
        this.aiServiceClient = aiServiceClient;
    }
    
    public String generateResumeSummary(String resumeText, String candidateName) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("resumeText", resumeText);
            request.put("candidateName", candidateName);
            request.put("operation", "resume_summary");
            
            Map<String, Object> response = aiServiceClient.generateResumeSummary(request);
            
            if (response != null && response.containsKey("summary")) {
                return (String) response.get("summary");
            } else {
                // Fallback to basic summary if AI service fails
                return generateBasicSummary(resumeText, candidateName);
            }
        } catch (Exception e) {
            System.err.println("Failed to generate AI resume summary: " + e.getMessage());
            return generateBasicSummary(resumeText, candidateName);
        }
    }
    
    private String generateBasicSummary(String resumeText, String candidateName) {
        if (resumeText == null || resumeText.trim().isEmpty()) {
            return "Resume uploaded for " + candidateName + " - content parsing pending.";
        }
        
        // Extract basic information from resume text
        StringBuilder summary = new StringBuilder();
        summary.append(candidateName).append(" - ");
        
        String lowerText = resumeText.toLowerCase();
        
        // Try to identify experience level
        if (lowerText.contains("senior") || lowerText.contains("lead")) {
            summary.append("Senior level professional");
        } else if (lowerText.contains("junior") || lowerText.contains("associate")) {
            summary.append("Junior level professional");
        } else {
            summary.append("Professional");
        }
        
        // Try to identify primary skills
        if (lowerText.contains("java") && lowerText.contains("spring")) {
            summary.append(" with Java & Spring Boot expertise");
        } else if (lowerText.contains("react") && lowerText.contains("javascript")) {
            summary.append(" with React & JavaScript expertise");
        } else if (lowerText.contains("python")) {
            summary.append(" with Python expertise");
        } else if (lowerText.contains("java")) {
            summary.append(" with Java expertise");
        }
        
        // Try to identify years of experience
        String[] lines = resumeText.split("\\n");
        for (String line : lines) {
            if (line.toLowerCase().contains("year") && line.toLowerCase().contains("experience")) {
                summary.append(". ").append(line.trim());
                break;
            }
        }
        
        summary.append(". Full resume analysis pending AI processing.");
        
        return summary.toString();
    }
    
    public ResumeSummaryResult processResumeSummary(String resumeText, String candidateName) {
        try {
            String summary = generateResumeSummary(resumeText, candidateName);
            return new ResumeSummaryResult(true, summary, null);
        } catch (Exception e) {
            String fallbackSummary = generateBasicSummary(resumeText, candidateName);
            return new ResumeSummaryResult(false, fallbackSummary, e.getMessage());
        }
    }
    
    public static class ResumeSummaryResult {
        private final boolean aiGenerated;
        private final String summary;
        private final String errorMessage;
        
        public ResumeSummaryResult(boolean aiGenerated, String summary, String errorMessage) {
            this.aiGenerated = aiGenerated;
            this.summary = summary;
            this.errorMessage = errorMessage;
        }
        
        public boolean isAiGenerated() { return aiGenerated; }
        public String getSummary() { return summary; }
        public String getErrorMessage() { return errorMessage; }
    }
}