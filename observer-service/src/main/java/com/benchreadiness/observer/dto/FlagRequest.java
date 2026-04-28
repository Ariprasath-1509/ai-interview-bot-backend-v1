package com.benchreadiness.observer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class FlagRequest {

    @NotBlank
    private String interviewId;

    @NotBlank @Size(min = 3)
    private String note;

    public String getInterviewId() { return interviewId; }
    public void setInterviewId(String interviewId) { this.interviewId = interviewId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
