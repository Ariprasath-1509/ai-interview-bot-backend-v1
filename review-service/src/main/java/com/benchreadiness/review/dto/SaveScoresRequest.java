package com.benchreadiness.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class SaveScoresRequest {

    @NotBlank
    private String interviewId;

    @NotEmpty
    private List<ScoreItem> scores;

    public record ScoreItem(String dimension, int value, String rationale, String evidence, String gap, String strengths, String weaknesses, String confidence) {}

    public String getInterviewId() { return interviewId; }
    public void setInterviewId(String interviewId) { this.interviewId = interviewId; }
    public List<ScoreItem> getScores() { return scores; }
    public void setScores(List<ScoreItem> scores) { this.scores = scores; }
}
