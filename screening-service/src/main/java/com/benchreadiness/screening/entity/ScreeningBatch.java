package com.benchreadiness.screening.entity;

import com.benchreadiness.screening.entity.enums.BatchStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "screening_batches", schema = "screening_svc")
public class ScreeningBatch {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false, length = 64)
    private String language;

    @Column(name = "concept_scope", columnDefinition = "text")
    private String conceptScope;

    @Column(name = "assigner_user_id", length = 36, nullable = false)
    private String assignerUserId;

    @Column(name = "assigner_name")
    private String assignerName;

    @Column(name = "assigner_email", nullable = false)
    private String assignerEmail;

    @Column(nullable = false)
    private Instant deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BatchStatus status = BatchStatus.OPEN;

    @Column(name = "report_sent_at")
    private Instant reportSentAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getConceptScope() { return conceptScope; }
    public void setConceptScope(String conceptScope) { this.conceptScope = conceptScope; }
    public String getAssignerUserId() { return assignerUserId; }
    public void setAssignerUserId(String assignerUserId) { this.assignerUserId = assignerUserId; }
    public String getAssignerName() { return assignerName; }
    public void setAssignerName(String assignerName) { this.assignerName = assignerName; }
    public String getAssignerEmail() { return assignerEmail; }
    public void setAssignerEmail(String assignerEmail) { this.assignerEmail = assignerEmail; }
    public Instant getDeadline() { return deadline; }
    public void setDeadline(Instant deadline) { this.deadline = deadline; }
    public BatchStatus getStatus() { return status; }
    public void setStatus(BatchStatus status) { this.status = status; }
    public Instant getReportSentAt() { return reportSentAt; }
    public void setReportSentAt(Instant reportSentAt) { this.reportSentAt = reportSentAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
