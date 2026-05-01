package com.benchreadiness.interview.dto;

public class TopMatchDTO {
    
    private Long jobPositionId;
    private String title;
    private String clientName;
    private Double matchScore;
    private String description;
    
    // Constructors
    public TopMatchDTO() {}
    
    public TopMatchDTO(Long jobPositionId, String title, String clientName, Double matchScore) {
        this.jobPositionId = jobPositionId;
        this.title = title;
        this.clientName = clientName;
        this.matchScore = matchScore;
    }
    
    public TopMatchDTO(Long jobPositionId, String title, String clientName, Double matchScore, String description) {
        this.jobPositionId = jobPositionId;
        this.title = title;
        this.clientName = clientName;
        this.matchScore = matchScore;
        this.description = description;
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
    
    public String getDescription() {
        return description;
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
    
    public void setDescription(String description) {
        this.description = description;
    }
}