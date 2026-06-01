package com.benchreadiness.ops.observer.dto;

import jakarta.validation.constraints.NotBlank;

public class InterviewAbandonedRequest {
    @NotBlank private String interviewId;
    @NotBlank private String createdByUserId;
    private String reason;

    public String getInterviewId() { return interviewId; }
    public void setInterviewId(String interviewId) { this.interviewId = interviewId; }
    public String getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(String createdByUserId) { this.createdByUserId = createdByUserId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
