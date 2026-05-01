package com.benchreadiness.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "deployment_history", schema = "auth_svc")
public class DeploymentHistory {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "candidate_id", nullable = false, length = 36)
    private String candidateId;

    @Column(name = "emp_id", length = 50)
    private String empId;

    @Column(name = "client_name", nullable = false)
    private String clientName;

    @Column(name = "deployed_date", nullable = false)
    private LocalDate deployedDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "mentor")
    private String mentor;

    @Column(name = "status", length = 20)
    private String status = "ACTIVE";  // ACTIVE, COMPLETED

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

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCandidateId() { return candidateId; }
    public void setCandidateId(String candidateId) { this.candidateId = candidateId; }

    public String getEmpId() { return empId; }
    public void setEmpId(String empId) { this.empId = empId; }

    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }

    public LocalDate getDeployedDate() { return deployedDate; }
    public void setDeployedDate(LocalDate deployedDate) { this.deployedDate = deployedDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public String getMentor() { return mentor; }
    public void setMentor(String mentor) { this.mentor = mentor; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
