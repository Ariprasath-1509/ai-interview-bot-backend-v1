package com.benchreadiness.interview.dto;

import java.util.UUID;

public class PositionRequirementDTO {
    
    private UUID id;
    private Integer candidatesNeeded;
    private Double minYoeRequired;
    private String source;
    
    // Constructors
    public PositionRequirementDTO() {}
    
    public PositionRequirementDTO(Integer candidatesNeeded, Double minYoeRequired, String source) {
        this.candidatesNeeded = candidatesNeeded;
        this.minYoeRequired = minYoeRequired;
        this.source = source;
    }
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public Integer getCandidatesNeeded() {
        return candidatesNeeded;
    }
    
    public void setCandidatesNeeded(Integer candidatesNeeded) {
        this.candidatesNeeded = candidatesNeeded;
    }
    
    public Double getMinYoeRequired() {
        return minYoeRequired;
    }
    
    public void setMinYoeRequired(Double minYoeRequired) {
        this.minYoeRequired = minYoeRequired;
    }
    
    public String getSource() {
        return source;
    }
    
    public void setSource(String source) {
        this.source = source;
    }
}