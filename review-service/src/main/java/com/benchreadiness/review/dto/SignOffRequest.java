package com.benchreadiness.review.dto;

import com.benchreadiness.review.entity.ReadinessVerdict;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class SignOffRequest {

    @NotBlank
    private String interviewId;

    @NotNull
    private ReadinessVerdict verdict;

    @NotBlank
    private String note;

    public String getInterviewId() { return interviewId; }
    public void setInterviewId(String interviewId) { this.interviewId = interviewId; }
    public ReadinessVerdict getVerdict() { return verdict; }
    public void setVerdict(ReadinessVerdict verdict) { this.verdict = verdict; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
