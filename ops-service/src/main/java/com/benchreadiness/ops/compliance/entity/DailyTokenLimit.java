package com.benchreadiness.ops.compliance.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "daily_token_limits", schema = "compliance_svc")
public class DailyTokenLimit {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "organization_id")
    private String organizationId = "default";

    @Column(name = "daily_limit", nullable = false)
    private Integer dailyLimit = 100000;

    @Column(name = "warning_threshold", nullable = false)
    private Integer warningThreshold = 80000;

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
    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
    public Integer getDailyLimit() { return dailyLimit; }
    public void setDailyLimit(Integer dailyLimit) { this.dailyLimit = dailyLimit; }
    public Integer getWarningThreshold() { return warningThreshold; }
    public void setWarningThreshold(Integer warningThreshold) { this.warningThreshold = warningThreshold; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
