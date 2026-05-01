package com.benchreadiness.interview.dto;

import java.util.List;

public class MatchResultDTO {
    
    private Long jobPositionId;
    private String title;
    private String clientName;
    private Double matchScore;
    private List<String> reasons;
    private List<String> missingSkills;
    private String experienceRange;
    private List<String> requiredSkills;
    
    // Detailed scoring breakdown
    private Double skillMatchScore;
    private Double experienceMatchScore;
    private Double feedbackScore;
    private Double questionRelevanceScore;
    
    // Constructors
    public MatchResultDTO() {}
    
    public MatchResultDTO(Long jobPositionId, String title, String clientName, Double matchScore, 
                         List<String> reasons, List<String> missingSkills, String experienceRange, 
                         List<String> requiredSkills, Double skillMatchScore, Double experienceMatchScore, 
                         Double feedbackScore, Double questionRelevanceScore) {
        this.jobPositionId = jobPositionId;
        this.title = title;
        this.clientName = clientName;
        this.matchScore = matchScore;
        this.reasons = reasons;
        this.missingSkills = missingSkills;
        this.experienceRange = experienceRange;
        this.requiredSkills = requiredSkills;
        this.skillMatchScore = skillMatchScore;
        this.experienceMatchScore = experienceMatchScore;
        this.feedbackScore = feedbackScore;
        this.questionRelevanceScore = questionRelevanceScore;
    }
    
    // Getters
    public Long getJobPositionId() {
        return jobPositionId;
    }
    
    public String getTitle() {
        return title;
    }
    
    public String getClientName() {
        return clientName;
    }
    
    public Double getMatchScore() {
        return matchScore;
    }
    
    public List<String> getReasons() {
        return reasons;
    }
    
    public List<String> getMissingSkills() {
        return missingSkills;
    }
    
    public String getExperienceRange() {
        return experienceRange;
    }
    
    public List<String> getRequiredSkills() {
        return requiredSkills;
    }
    
    public Double getSkillMatchScore() {
        return skillMatchScore;
    }
    
    public Double getExperienceMatchScore() {
        return experienceMatchScore;
    }
    
    public Double getFeedbackScore() {
        return feedbackScore;
    }
    
    public Double getQuestionRelevanceScore() {
        return questionRelevanceScore;
    }
    
    // Setters
    public void setJobPositionId(Long jobPositionId) {
        this.jobPositionId = jobPositionId;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public void setClientName(String clientName) {
        this.clientName = clientName;
    }
    
    public void setMatchScore(Double matchScore) {
        this.matchScore = matchScore;
    }
    
    public void setReasons(List<String> reasons) {
        this.reasons = reasons;
    }
    
    public void setMissingSkills(List<String> missingSkills) {
        this.missingSkills = missingSkills;
    }
    
    public void setExperienceRange(String experienceRange) {
        this.experienceRange = experienceRange;
    }
    
    public void setRequiredSkills(List<String> requiredSkills) {
        this.requiredSkills = requiredSkills;
    }
    
    public void setSkillMatchScore(Double skillMatchScore) {
        this.skillMatchScore = skillMatchScore;
    }
    
    public void setExperienceMatchScore(Double experienceMatchScore) {
        this.experienceMatchScore = experienceMatchScore;
    }
    
    public void setFeedbackScore(Double feedbackScore) {
        this.feedbackScore = feedbackScore;
    }
    
    public void setQuestionRelevanceScore(Double questionRelevanceScore) {
        this.questionRelevanceScore = questionRelevanceScore;
    }
}