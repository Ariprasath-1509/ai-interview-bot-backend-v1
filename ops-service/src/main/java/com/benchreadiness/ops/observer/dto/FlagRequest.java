package com.benchreadiness.ops.observer.dto;

import jakarta.validation.constraints.NotBlank;

public class FlagRequest {
    @NotBlank private String interviewId;
    private String note;

    public String getInterviewId() { return interviewId; }
    public void setInterviewId(String interviewId) { this.interviewId = interviewId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
