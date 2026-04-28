package com.benchreadiness.review.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "sign_offs", schema = "review_svc")
public class SignOff {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "interview_id", nullable = false, unique = true)
    private String interviewId;

    @Column(name = "reviewer_user_id", nullable = false)
    private String reviewerUserId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "final_verdict", nullable = false, columnDefinition = "review_svc.readiness_verdict")
    private ReadinessVerdict finalVerdict;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String note;

    @Column(name = "signed_off_at", nullable = false)
    private Instant signedOffAt = Instant.now();

    @PrePersist
    @PreUpdate
    void prePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        signedOffAt = Instant.now();
    }

    public String getId() { return id; }
    public String getInterviewId() { return interviewId; }
    public void setInterviewId(String interviewId) { this.interviewId = interviewId; }
    public String getReviewerUserId() { return reviewerUserId; }
    public void setReviewerUserId(String reviewerUserId) { this.reviewerUserId = reviewerUserId; }
    public ReadinessVerdict getFinalVerdict() { return finalVerdict; }
    public void setFinalVerdict(ReadinessVerdict finalVerdict) { this.finalVerdict = finalVerdict; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Instant getSignedOffAt() { return signedOffAt; }
}
