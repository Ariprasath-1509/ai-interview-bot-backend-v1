package com.benchreadiness.interview.dto;

import com.benchreadiness.interview.entity.SkillSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SkillRequirementDTO {
    
    private UUID id;
    private SkillSet skillSet;
    private List<PositionRequirementDTO> positions = new ArrayList<>();
    
    // Constructors
    public SkillRequirementDTO() {}
    
    public SkillRequirementDTO(SkillSet skillSet) {
        this.skillSet = skillSet;
    }
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public SkillSet getSkillSet() {
        return skillSet;
    }
    
    public void setSkillSet(SkillSet skillSet) {
        this.skillSet = skillSet;
    }
    
    public List<PositionRequirementDTO> getPositions() {
        return positions;
    }
    
    public void setPositions(List<PositionRequirementDTO> positions) {
        this.positions = positions;
    }
}