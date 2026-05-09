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
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SkillSet skillSet;
    
    @OneToMany(mappedBy = "skillRequirement", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<PositionRequirement> positions = new ArrayList<>();
    
    // Constructors
    public SkillRequirement() {}
    
    public SkillRequirement(Client client, SkillSet skillSet) {
        this.client = client;
        this.skillSet = skillSet;
    }
    
    // Getters and Setters
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
    
    public SkillSet getSkillSet() {
        return skillSet;
    }
    
    public void setSkillSet(SkillSet skillSet) {
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