package com.benchreadiness.ops.observer.dto;

import jakarta.validation.constraints.NotBlank;

public class InterviewCreatedRequest {
    @NotBlank private String interviewId;
    @NotBlank private String engineerEmail;
    private String engineerName;

    public String getInterviewId() { return interviewId; }
    public void setInterviewId(String interviewId) { this.interviewId = interviewId; }
    public String getEngineerEmail() { return engineerEmail; }
    public void setEngineerEmail(String engineerEmail) { this.engineerEmail = engineerEmail; }
    public String getEngineerName() { return engineerName; }
    public void setEngineerName(String engineerName) { this.engineerName = engineerName; }
}
