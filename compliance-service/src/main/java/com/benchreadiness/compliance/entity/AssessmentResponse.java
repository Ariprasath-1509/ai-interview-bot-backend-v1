package com.benchreadiness.compliance.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "assessment_responses", schema = "compliance_svc")
public class AssessmentResponse {
    
    @Id
    @Column(name = "id")
    private String id;
    
    @Column(name = "interview_id", nullable = false, unique = true)
    private String interviewId;
    
    @Column(name = "assessment_json", nullable = false, columnDefinition = "TEXT")
    private String assessmentJson;
    
    @Column(name = "tokens_used", nullable = false)
    private Integer tokensUsed = 0;
    
    @Column(name = "assessment_source", nullable = false)
    private String assessmentSource = "claude-two-pass";
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = java.util.UUID.randomUUID().toString();
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getInterviewId() { return interviewId; }
    public void setInterviewId(String interviewId) { this.interviewId = interviewId; }
    
    public String getAssessmentJson() { return assessmentJson; }
    public void setAssessmentJson(String assessmentJson) { this.assessmentJson = assessmentJson; }
    
    public Integer getTokensUsed() { return tokensUsed; }
    public void setTokensUsed(Integer tokensUsed) { this.tokensUsed = tokensUsed; }
    
    public String getAssessmentSource() { return assessmentSource; }
    public void setAssessmentSource(String assessmentSource) { this.assessmentSource = assessmentSource; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}