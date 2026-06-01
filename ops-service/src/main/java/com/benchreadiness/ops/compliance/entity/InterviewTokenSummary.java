package com.benchreadiness.ops.compliance.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "interview_token_summary", schema = "compliance_svc")
public class InterviewTokenSummary {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "interview_id", nullable = false, unique = true)
    private String interviewId;

    @Column(name = "total_tokens", nullable = false)
    private Integer totalTokens = 0;

    @Column(name = "total_cost_usd", precision = 10, scale = 6)
    private BigDecimal totalCostUsd = BigDecimal.ZERO;

    @Column(name = "question_tokens", nullable = false)
    private Integer questionTokens = 0;

    @Column(name = "assessment_tokens", nullable = false)
    private Integer assessmentTokens = 0;

    @Column(name = "rubric_tokens", nullable = false)
    private Integer rubricTokens = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getInterviewId() { return interviewId; }
    public void setInterviewId(String interviewId) { this.interviewId = interviewId; }
    public Integer getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Integer totalTokens) { this.totalTokens = totalTokens; }
    public BigDecimal getTotalCostUsd() { return totalCostUsd; }
    public void setTotalCostUsd(BigDecimal totalCostUsd) { this.totalCostUsd = totalCostUsd; }
    public Integer getQuestionTokens() { return questionTokens; }
    public void setQuestionTokens(Integer questionTokens) { this.questionTokens = questionTokens; }
    public Integer getAssessmentTokens() { return assessmentTokens; }
    public void setAssessmentTokens(Integer assessmentTokens) { this.assessmentTokens = assessmentTokens; }
    public Integer getRubricTokens() { return rubricTokens; }
    public void setRubricTokens(Integer rubricTokens) { this.rubricTokens = rubricTokens; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
