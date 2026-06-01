package com.benchreadiness.ops.observer.dto;

import jakarta.validation.constraints.NotBlank;

public class InjectRequest {
    @NotBlank private String interviewId;
    @NotBlank private String question;
    private String mode = "INJECT";

    public String getInterviewId() { return interviewId; }
    public void setInterviewId(String interviewId) { this.interviewId = interviewId; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
}
