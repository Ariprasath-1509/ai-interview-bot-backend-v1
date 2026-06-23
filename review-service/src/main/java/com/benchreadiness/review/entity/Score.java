package com.benchreadiness.review.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "scores", schema = "review_svc")
public class Score {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "interview_id", nullable = false)
    private String interviewId;

    @Column(nullable = false, length = 100)
    private String dimension;

    @Column(nullable = false)
    private int value;

    @Column(columnDefinition = "TEXT")
    private String rationale;

    @Column(columnDefinition = "TEXT")
    private String evidence;

    @Column(columnDefinition = "TEXT")
    private String gap;

    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Column(columnDefinition = "TEXT")
    private String weaknesses;

    @Column(length = 10)
    private String confidence;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @PrePersist
    void prePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getInterviewId() { return interviewId; }
    public void setInterviewId(String interviewId) { this.interviewId = interviewId; }
    public String getDimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }
    public int getValue() { return value; }
    public void setValue(int value) { this.value = value; }
    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }
    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }
    public String getGap() { return gap; }
    public void setGap(String gap) { this.gap = gap; }
    public String getStrengths() { return strengths; }
    public void setStrengths(String strengths) { this.strengths = strengths; }
    public String getWeaknesses() { return weaknesses; }
    public void setWeaknesses(String weaknesses) { this.weaknesses = weaknesses; }
    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }
    public Instant getCreatedAt() { return createdAt; }
}
