package com.benchreadiness.interview.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "interview_questions", schema = "interview_svc")
public class InterviewQuestion {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "interview_id", nullable = false)
    private String interviewId;

    @Column(name = "slot_number", nullable = false)
    private int slotNumber;
    
    @Column(name = "question_text", columnDefinition = "TEXT", nullable = false)
    private String questionText;
    
    @Column(name = "candidate_answer", columnDefinition = "TEXT")
    private String candidateAnswer;
    
    @ElementCollection
    @CollectionTable(name = "question_tags", schema = "interview_svc", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "tag")
    private List<String> tags; // e.g., Java, Spring, Algorithm
    
    @Column(name = "difficulty_level")
    private String difficultyLevel; // EASY, MEDIUM, HARD
    
    @Column(name = "question_type")
    private String questionType; // TECHNICAL, BEHAVIORAL, CODING
    
    @Column(name = "asked_at")
    private LocalDateTime askedAt;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    @Column(name = "question_bank_id")
    private String questionBankId;

    @Column(name = "source")
    private String source;
    
    // Constructors
    public InterviewQuestion() {}
    
    public InterviewQuestion(String interviewId, int slotNumber, String questionText) {
        this.interviewId = interviewId;
        this.slotNumber = slotNumber;
        this.questionText = questionText;
    }
    
    public InterviewQuestion(Long id, String interviewId, String questionText, String candidateAnswer, 
                           List<String> tags, String difficultyLevel, String questionType, LocalDateTime askedAt) {
        this.id = id;
        this.interviewId = interviewId;
        this.questionText = questionText;
        this.candidateAnswer = candidateAnswer;
        this.tags = tags;
        this.difficultyLevel = difficultyLevel;
        this.questionType = questionType;
        this.askedAt = askedAt;
    }
    
    // Getters
    public Long getId() {
        return id;
    }
    
    public String getInterviewId() {
        return interviewId;
    }

    public int getSlotNumber() {
        return slotNumber;
    }
    
    public String getQuestionText() {
        return questionText;
    }
    
    public String getCandidateAnswer() {
        return candidateAnswer;
    }
    
    public List<String> getTags() {
        return tags;
    }
    
    public String getDifficultyLevel() {
        return difficultyLevel;
    }
    
    public String getQuestionType() {
        return questionType;
    }
    
    public LocalDateTime getAskedAt() {
        return askedAt;
    }

    public LocalDateTime getAnsweredAt() {
        return answeredAt;
    }

    public String getQuestionBankId() {
        return questionBankId;
    }

    public String getSource() {
        return source;
    }
    
    // Setters
    public void setId(Long id) {
        this.id = id;
    }
    
    public void setInterviewId(String interviewId) {
        this.interviewId = interviewId;
    }

    public void setSlotNumber(int slotNumber) {
        this.slotNumber = slotNumber;
    }
    
    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }
    
    public void setCandidateAnswer(String candidateAnswer) {
        this.candidateAnswer = candidateAnswer;
    }
    
    public void setTags(List<String> tags) {
        this.tags = tags;
    }
    
    public void setDifficultyLevel(String difficultyLevel) {
        this.difficultyLevel = difficultyLevel;
    }
    
    public void setQuestionType(String questionType) {
        this.questionType = questionType;
    }
    
    public void setAskedAt(LocalDateTime askedAt) {
        this.askedAt = askedAt;
    }

    public void setAnsweredAt(LocalDateTime answeredAt) {
        this.answeredAt = answeredAt;
    }

    public void setQuestionBankId(String questionBankId) {
        this.questionBankId = questionBankId;
    }

    public void setSource(String source) {
        this.source = source;
    }
    
    @PrePersist
    protected void onCreate() {
        askedAt = LocalDateTime.now();
    }
}