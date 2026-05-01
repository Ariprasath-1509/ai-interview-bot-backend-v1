package com.benchreadiness.interview.dto;

public class AbandonInterviewRequest {
    private String transcriptJson;
    private String reason; // "not_prepared" | "time_expired"
    
    // Constructors
    public AbandonInterviewRequest() {}
    
    public AbandonInterviewRequest(String transcriptJson, String reason) {
        this.transcriptJson = transcriptJson;
        this.reason = reason;
    }

    public String getTranscriptJson() { return transcriptJson; }
    public void setTranscriptJson(String transcriptJson) { this.transcriptJson = transcriptJson; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}