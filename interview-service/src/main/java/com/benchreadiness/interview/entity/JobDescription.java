package com.benchreadiness.interview.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "job_descriptions", schema = "interview_svc")
public class JobDescription {

    @Id
    @Column(length = 36)
    private String id;

    private String title;
    private String source;

    @Column(columnDefinition = "TEXT")
    private String text;

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
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
