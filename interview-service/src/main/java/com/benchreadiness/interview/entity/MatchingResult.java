package com.benchreadiness.interview.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "matching_results", schema = "interview_svc")
public class MatchingResult {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "candidate_id", nullable = false)
    private Long candidateId;
    
    @Column(name = "job_position_id", nullable = false)
    private Long jobPositionId;
    
    @Column(name = "match_score", nullable = false)
    private Double matchScore; // 0-100
    
    @ElementCollection
    @CollectionTable(name = "match_reasons", schema = "interview_svc", joinColumns = @JoinColumn(name = "matching_result_id"))
    @Column(name = "reason")
    private List<String> reasons;
    
    @ElementCollection
    @CollectionTable(name = "missing_skills", schema = "interview_svc", joinColumns = @JoinColumn(name = "matching_result_id"))
    @Column(name = "skill")
    private List<String> missingSkills;
    
    @Column(name = "skill_match_score")
    private Double skillMatchScore;
    
    @Column(name = "experience_match_score")
    private Double experienceMatchScore;
    
    @Column(name = "feedback_score")
    private Double feedbackScore;
    
    @Column(name = "question_relevance_score")
    private Double questionRelevanceScore;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt; // Cache expiry
    
    // Constructors
    public MatchingResult() {}
    
    public MatchingResult(Long candidateId, Long jobPositionId, Double matchScore) {
        this.candidateId = candidateId;
        this.jobPositionId = jobPositionId;
        this.matchScore = matchScore;
    }
    
    public MatchingResult(Long id, Long candidateId, Long jobPositionId, Double matchScore, 
                         List<String> reasons, List<String> missingSkills, Double skillMatchScore, 
                         Double experienceMatchScore, Double feedbackScore, Double questionRelevanceScore, 
                         LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.id = id;
        this.candidateId = candidateId;
        this.jobPositionId = jobPositionId;
        this.matchScore = matchScore;
        this.reasons = reasons;
        this.missingSkills = missingSkills;
        this.skillMatchScore = skillMatchScore;
        this.experienceMatchScore = experienceMatchScore;
        this.feedbackScore = feedbackScore;
        this.questionRelevanceScore = questionRelevanceScore;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }
    
    // Getters
    public Long getId() {
        return id;
    }
    
    public Long getCandidateId() {
        return candidateId;
    }
    
    public Long getJobPositionId() {
        return jobPositionId;
    }
    
    public Double getMatchScore() {
        return matchScore;
    }
    
    public List<String> getReasons() {
        return reasons;
    }
    
    public List<String> getMissingSkills() {
        return missingSkills;
    }
    
    public Double getSkillMatchScore() {
        return skillMatchScore;
    }
    
    public Double getExperienceMatchScore() {
        return experienceMatchScore;
    }
    
    public Double getFeedbackScore() {
        return feedbackScore;
    }
    
    public Double getQuestionRelevanceScore() {
        return questionRelevanceScore;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
    
    // Setters
    public void setId(Long id) {
        this.id = id;
    }
    
    public void setCandidateId(Long candidateId) {
        this.candidateId = candidateId;
    }
    
    public void setJobPositionId(Long jobPositionId) {
        this.jobPositionId = jobPositionId;
    }
    
    public void setMatchScore(Double matchScore) {
        this.matchScore = matchScore;
    }
    
    public void setReasons(List<String> reasons) {
        this.reasons = reasons;
    }
    
    public void setMissingSkills(List<String> missingSkills) {
        this.missingSkills = missingSkills;
    }
    
    public void setSkillMatchScore(Double skillMatchScore) {
        this.skillMatchScore = skillMatchScore;
    }
    
    public void setExperienceMatchScore(Double experienceMatchScore) {
        this.experienceMatchScore = experienceMatchScore;
    }
    
    public void setFeedbackScore(Double feedbackScore) {
        this.feedbackScore = feedbackScore;
    }
    
    public void setQuestionRelevanceScore(Double questionRelevanceScore) {
        this.questionRelevanceScore = questionRelevanceScore;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        expiresAt = LocalDateTime.now().plusHours(24); // Cache for 24 hours
    }
}