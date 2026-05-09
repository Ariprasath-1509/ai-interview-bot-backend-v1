package com.benchreadiness.interview.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "position_requirements", schema = "interview_svc")
public class PositionRequirement {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_requirement_id", nullable = false)
    private SkillRequirement skillRequirement;
    
    @Column(nullable = false)
    private Integer candidatesNeeded;
    
    @Column(nullable = false)
    private Double minYoeRequired;
    
    @Column(nullable = false)
    private String source; // BENCH_B2B, MARKET
    
    // Constructors
    public PositionRequirement() {}
    
    public PositionRequirement(SkillRequirement skillRequirement, Integer candidatesNeeded, 
                              Double minYoeRequired, String source) {
        this.skillRequirement = skillRequirement;
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
    
    public SkillRequirement getSkillRequirement() {
        return skillRequirement;
    }
    
    public void setSkillRequirement(SkillRequirement skillRequirement) {
        this.skillRequirement = skillRequirement;
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