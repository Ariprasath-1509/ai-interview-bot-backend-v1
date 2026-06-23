package com.benchreadiness.interview.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SkillRequirementDTO {
    
    private UUID id;
    private String skillSet;
    private List<PositionRequirementDTO> positions = new ArrayList<>();
    
    public SkillRequirementDTO() {}
    
    public SkillRequirementDTO(String skillSet) {
        this.skillSet = skillSet;
    }
    
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public String getSkillSet() {
        return skillSet;
    }
    
    public void setSkillSet(String skillSet) {
        this.skillSet = skillSet;
    }
    
    public List<PositionRequirementDTO> getPositions() {
        return positions;
    }
    
    public void setPositions(List<PositionRequirementDTO> positions) {
        this.positions = positions;
    }
}
