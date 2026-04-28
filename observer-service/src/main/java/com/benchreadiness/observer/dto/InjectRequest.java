package com.benchreadiness.observer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class InjectRequest {

    @NotBlank
    private String interviewId;

    @NotBlank
    private String mode; // SOFT_INJECT | HARD_INJECT

    @NotBlank @Size(min = 5)
    private String question;

    public String getInterviewId() { return interviewId; }
    public void setInterviewId(String interviewId) { this.interviewId = interviewId; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
}
