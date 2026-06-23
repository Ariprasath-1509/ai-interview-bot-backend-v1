package com.benchreadiness.interview.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "interview_plans", schema = "interview_svc")
public class InterviewPlan {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "engineer_id", nullable = false)
    private String engineerId;

    @Column(name = "jd_id", nullable = false)
    private String jdId;

    @Column(name = "slots_json", nullable = false, columnDefinition = "TEXT")
    private String slotsJson;

    @Column(name = "gap_map_json", nullable = false, columnDefinition = "TEXT")
    private String gapMapJson;

    @Column(name = "rubric_json", columnDefinition = "TEXT")
    private String rubricJson;

    @Column(name = "candidate_profile_json", columnDefinition = "TEXT")
    private String candidateProfileJson;

    @Column(name = "created_by_user_id", nullable = false)
    private String createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist
    void prePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public String getId() { return id; }
    public String getEngineerId() { return engineerId; }
    public void setEngineerId(String engineerId) { this.engineerId = engineerId; }
    public String getJdId() { return jdId; }
    public void setJdId(String jdId) { this.jdId = jdId; }
    public String getSlotsJson() { return slotsJson; }
    public void setSlotsJson(String slotsJson) { this.slotsJson = slotsJson; }
    public String getGapMapJson() { return gapMapJson; }
    public void setGapMapJson(String gapMapJson) { this.gapMapJson = gapMapJson; }
    public String getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(String createdByUserId) { this.createdByUserId = createdByUserId; }
    public String getRubricJson() { return rubricJson; }
    public void setRubricJson(String rubricJson) { this.rubricJson = rubricJson; }
    public String getCandidateProfileJson() { return candidateProfileJson; }
    public void setCandidateProfileJson(String candidateProfileJson) { this.candidateProfileJson = candidateProfileJson; }
}
