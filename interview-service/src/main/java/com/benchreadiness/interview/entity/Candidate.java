package com.benchreadiness.interview.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "candidates", schema = "interview_svc")
public class Candidate {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(unique = true, nullable = false)
    private String email;
    
    @ElementCollection
    @CollectionTable(name = "candidate_skills", schema = "interview_svc", joinColumns = @JoinColumn(name = "candidate_id"))
    @Column(name = "skill")
    private List<String> skills;
    
    @Column(name = "experience_years")
    private Integer experienceYears;
    
    @Column(name = "resume_text", columnDefinition = "TEXT")
    private String resumeText;
    
    @Column(name = "education")
    private String education;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Constructors
    public Candidate() {}
    
    public Candidate(String name, String email) {
        this.name = name;
        this.email = email;
    }
    
    public Candidate(Long id, String name, String email, List<String> skills, Integer experienceYears, 
                    String resumeText, String education, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.skills = skills;
        this.experienceYears = experienceYears;
        this.resumeText = resumeText;
        this.education = education;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
    
    // Getters
    public Long getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public String getEmail() {
        return email;
    }
    
    public List<String> getSkills() {
        return skills;
    }
    
    public Integer getExperienceYears() {
        return experienceYears;
    }
    
    public String getResumeText() {
        return resumeText;
    }
    
    public String getEducation() {
        return education;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    // Setters
    public void setId(Long id) {
        this.id = id;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public void setSkills(List<String> skills) {
        this.skills = skills;
    }
    
    public void setExperienceYears(Integer experienceYears) {
        this.experienceYears = experienceYears;
    }
    
    public void setResumeText(String resumeText) {
        this.resumeText = resumeText;
    }
    
    public void setEducation(String education) {
        this.education = education;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}