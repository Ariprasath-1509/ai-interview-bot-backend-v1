package com.benchreadiness.interview.dto;

public class AbandonInterviewRequest {
    private String transcriptJson;
    private String reason; // "not_prepared" | "time_expired"

    public String getTranscriptJson() { return transcriptJson; }
    public void setTranscriptJson(String transcriptJson) { this.transcriptJson = transcriptJson; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
