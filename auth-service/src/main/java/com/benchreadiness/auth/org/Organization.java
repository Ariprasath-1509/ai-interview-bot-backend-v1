package com.benchreadiness.auth.org;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "organizations", schema = "auth_svc")
public class Organization {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrganizationType type = OrganizationType.LIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrganizationStatus status = OrganizationStatus.ACTIVE;

    /** Null means unlimited — only meaningful for DEMO orgs. */
    @Column(name = "max_interviews")
    private Integer maxInterviews;

    @Column(name = "max_candidates")
    private Integer maxCandidates;

    @Column(name = "max_clients")
    private Integer maxClients;

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
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public OrganizationType getType() { return type; }
    public void setType(OrganizationType type) { this.type = type; }
    public OrganizationStatus getStatus() { return status; }
    public void setStatus(OrganizationStatus status) { this.status = status; }
    public Integer getMaxInterviews() { return maxInterviews; }
    public void setMaxInterviews(Integer maxInterviews) { this.maxInterviews = maxInterviews; }
    public Integer getMaxCandidates() { return maxCandidates; }
    public void setMaxCandidates(Integer maxCandidates) { this.maxCandidates = maxCandidates; }
    public Integer getMaxClients() { return maxClients; }
    public void setMaxClients(Integer maxClients) { this.maxClients = maxClients; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
