package com.benchreadiness.interview.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "interview_feedback", schema = "interview_svc")
public class InterviewFeedback {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "interview_id", nullable = false)
    private String interviewId;
    
    @Column(nullable = false)
    private Integer rating; // 1-5 scale
    
    @Column(columnDefinition = "TEXT")
    private String comments;
    
    @ElementCollection
    @CollectionTable(name = "feedback_strengths", schema = "interview_svc", joinColumns = @JoinColumn(name = "feedback_id"))
    @Column(name = "strength")
    private List<String> strengths;
    
    @ElementCollection
    @CollectionTable(name = "feedback_weaknesses", schema = "interview_svc", joinColumns = @JoinColumn(name = "feedback_id"))
    @Column(name = "weakness")
    private List<String> weaknesses;
    
    @Column(name = "technical_score")
    private Integer technicalScore; // 1-5
    
    @Column(name = "communication_score")
    private Integer communicationScore; // 1-5
    
    @Column(name = "problem_solving_score")
    private Integer problemSolvingScore; // 1-5
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "created_by")
    private String createdBy;
    
    // Constructors
    public InterviewFeedback() {}
    
    public InterviewFeedback(String interviewId, Integer rating) {
        this.interviewId = interviewId;
        this.rating = rating;
    }
    
    public InterviewFeedback(Long id, String interviewId, Integer rating, String comments, 
                           List<String> strengths, List<String> weaknesses, Integer technicalScore, 
                           Integer communicationScore, Integer problemSolvingScore, 
                           LocalDateTime createdAt, String createdBy) {
        this.id = id;
        this.interviewId = interviewId;
        this.rating = rating;
        this.comments = comments;
        this.strengths = strengths;
        this.weaknesses = weaknesses;
        this.technicalScore = technicalScore;
        this.communicationScore = communicationScore;
        this.problemSolvingScore = problemSolvingScore;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }
    
    // Getters
    public Long getId() {
        return id;
    }
    
    public String getInterviewId() {
        return interviewId;
    }
    
    public Integer getRating() {
        return rating;
    }
    
    public String getComments() {
        return comments;
    }
    
    public List<String> getStrengths() {
        return strengths;
    }
    
    public List<String> getWeaknesses() {
        return weaknesses;
    }
    
    public Integer getTechnicalScore() {
        return technicalScore;
    }
    
    public Integer getCommunicationScore() {
        return communicationScore;
    }
    
    public Integer getProblemSolvingScore() {
        return problemSolvingScore;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public String getCreatedBy() {
        return createdBy;
    }
    
    // Setters
    public void setId(Long id) {
        this.id = id;
    }
    
    public void setInterviewId(String interviewId) {
        this.interviewId = interviewId;
    }
    
    public void setRating(Integer rating) {
        this.rating = rating;
    }
    
    public void setComments(String comments) {
        this.comments = comments;
    }
    
    public void setStrengths(List<String> strengths) {
        this.strengths = strengths;
    }
    
    public void setWeaknesses(List<String> weaknesses) {
        this.weaknesses = weaknesses;
    }
    
    public void setTechnicalScore(Integer technicalScore) {
        this.technicalScore = technicalScore;
    }
    
    public void setCommunicationScore(Integer communicationScore) {
        this.communicationScore = communicationScore;
    }
    
    public void setProblemSolvingScore(Integer problemSolvingScore) {
        this.problemSolvingScore = problemSolvingScore;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}