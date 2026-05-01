package com.benchreadiness.interview.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public class ClientDTO {
    
    private UUID id;
    private String clientName;
    private String jdRole;
    private String jdDescription;
    private Integer positionsVacant;
    private Integer marketCandidatesNeeded;
    private Integer benchB2bCandidatesNeeded;
    private String status;
    private Boolean benchReviewed;
    private Boolean recruitmentReviewed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Constructors
    public ClientDTO() {}
    
    public ClientDTO(UUID id, String clientName, String jdRole, String jdDescription,
                    Integer positionsVacant, Integer marketCandidatesNeeded, 
                    Integer benchB2bCandidatesNeeded, String status, 
                    Boolean benchReviewed, Boolean recruitmentReviewed,
                    LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.clientName = clientName;
        this.jdRole = jdRole;
        this.jdDescription = jdDescription;
        this.positionsVacant = positionsVacant;
        this.marketCandidatesNeeded = marketCandidatesNeeded;
        this.benchB2bCandidatesNeeded = benchB2bCandidatesNeeded;
        this.status = status;
        this.benchReviewed = benchReviewed;
        this.recruitmentReviewed = recruitmentReviewed;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
    
    // Getters
    public UUID getId() {
        return id;
    }
    
    public String getClientName() {
        return clientName;
    }
    
    public String getJdRole() {
        return jdRole;
    }
    
    public String getJdDescription() {
        return jdDescription;
    }
    
    public Integer getPositionsVacant() {
        return positionsVacant;
    }
    
    public Integer getMarketCandidatesNeeded() {
        return marketCandidatesNeeded;
    }
    
    public Integer getBenchB2bCandidatesNeeded() {
        return benchB2bCandidatesNeeded;
    }
    
    public String getStatus() {
        return status;
    }
    
    public Boolean getBenchReviewed() {
        return benchReviewed;
    }
    
    public Boolean getRecruitmentReviewed() {
        return recruitmentReviewed;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    // Setters
    public void setId(UUID id) {
        this.id = id;
    }
    
    public void setClientName(String clientName) {
        this.clientName = clientName;
    }
    
    public void setJdRole(String jdRole) {
        this.jdRole = jdRole;
    }
    
    public void setJdDescription(String jdDescription) {
        this.jdDescription = jdDescription;
    }
    
    public void setPositionsVacant(Integer positionsVacant) {
        this.positionsVacant = positionsVacant;
    }
    
    public void setMarketCandidatesNeeded(Integer marketCandidatesNeeded) {
        this.marketCandidatesNeeded = marketCandidatesNeeded;
    }
    
    public void setBenchB2bCandidatesNeeded(Integer benchB2bCandidatesNeeded) {
        this.benchB2bCandidatesNeeded = benchB2bCandidatesNeeded;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public void setBenchReviewed(Boolean benchReviewed) {
        this.benchReviewed = benchReviewed;
    }
    
    public void setRecruitmentReviewed(Boolean recruitmentReviewed) {
        this.recruitmentReviewed = recruitmentReviewed;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}