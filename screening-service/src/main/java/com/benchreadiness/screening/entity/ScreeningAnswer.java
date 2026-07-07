package com.benchreadiness.screening.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "screening_answers", schema = "screening_svc")
public class ScreeningAnswer {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false)
    private ScreeningCandidate candidate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private ScreeningQuestion question;

    @Column(name = "raw_answer", columnDefinition = "text")
    private String rawAnswer;

    private Double score;

    @Column(name = "ai_feedback", columnDefinition = "text")
    private String aiFeedback;

    @Column(name = "graded_at")
    private Instant gradedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public ScreeningCandidate getCandidate() { return candidate; }
    public void setCandidate(ScreeningCandidate candidate) { this.candidate = candidate; }
    public ScreeningQuestion getQuestion() { return question; }
    public void setQuestion(ScreeningQuestion question) { this.question = question; }
    public String getRawAnswer() { return rawAnswer; }
    public void setRawAnswer(String rawAnswer) { this.rawAnswer = rawAnswer; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public String getAiFeedback() { return aiFeedback; }
    public void setAiFeedback(String aiFeedback) { this.aiFeedback = aiFeedback; }
    public Instant getGradedAt() { return gradedAt; }
    public void setGradedAt(Instant gradedAt) { this.gradedAt = gradedAt; }
}
