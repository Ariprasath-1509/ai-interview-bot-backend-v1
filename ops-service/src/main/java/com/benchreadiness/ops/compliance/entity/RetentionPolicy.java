package com.benchreadiness.ops.compliance.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "retention_policies", schema = "compliance_svc")
public class RetentionPolicy {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 100)
    private String region;

    @Column(name = "transcript_days", nullable = false)
    private int transcriptDays = 365;

    @Column(name = "audio_days", nullable = false)
    private int audioDays = 90;

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
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public int getTranscriptDays() { return transcriptDays; }
    public void setTranscriptDays(int transcriptDays) { this.transcriptDays = transcriptDays; }
    public int getAudioDays() { return audioDays; }
    public void setAudioDays(int audioDays) { this.audioDays = audioDays; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
