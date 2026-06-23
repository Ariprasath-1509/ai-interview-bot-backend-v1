package com.benchreadiness.ops.observer.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "observer_events", schema = "observer_svc")
public class ObserverEvent {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "interview_id", nullable = false)
    private String interviewId;

    @Column(name = "observer_user_id", nullable = false)
    private String observerUserId;

    @Column(nullable = false, length = 50)
    private String kind;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

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
    public String getObserverUserId() { return observerUserId; }
    public void setObserverUserId(String observerUserId) { this.observerUserId = observerUserId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public Instant getCreatedAt() { return createdAt; }
}
