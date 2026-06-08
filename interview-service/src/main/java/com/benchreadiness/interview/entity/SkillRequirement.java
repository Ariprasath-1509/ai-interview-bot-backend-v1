package com.benchreadiness.interview.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "skill_requirements", schema = "interview_svc")
public class SkillRequirement {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;
    
    @Column(name = "skill_set", nullable = false, length = 128)
    private String skillSet;
    
    @OneToMany(mappedBy = "skillRequirement", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<PositionRequirement> positions = new ArrayList<>();
    
    public SkillRequirement() {}
    
    public SkillRequirement(Client client, String skillSet) {
        this.client = client;
        this.skillSet = skillSet;
    }
    
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public Client getClient() {
        return client;
    }
    
    public void setClient(Client client) {
        this.client = client;
    }
    
    public String getSkillSet() {
        return skillSet;
    }
    
    public void setSkillSet(String skillSet) {
        this.skillSet = skillSet;
    }
    
    public List<PositionRequirement> getPositions() {
        return positions;
    }
    
    public void setPositions(List<PositionRequirement> positions) {
        this.positions = positions;
    }
    
    public void addPosition(PositionRequirement position) {
        positions.add(position);
        position.setSkillRequirement(this);
    }
}
