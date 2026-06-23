package com.benchreadiness.interview.dto;

public class CompleteInterviewRequest {
    private String transcriptJson;
    private String proposedVerdict;
    private String finalVerdict;
    private String status;
    
    // Constructors
    public CompleteInterviewRequest() {}
    
    public CompleteInterviewRequest(String transcriptJson, String proposedVerdict, String finalVerdict, String status) {
        this.transcriptJson = transcriptJson;
        this.proposedVerdict = proposedVerdict;
        this.finalVerdict = finalVerdict;
        this.status = status;
    }

    public String getTranscriptJson() { return transcriptJson; }
    public void setTranscriptJson(String transcriptJson) { this.transcriptJson = transcriptJson; }
    public String getProposedVerdict() { return proposedVerdict; }
    public void setProposedVerdict(String proposedVerdict) { this.proposedVerdict = proposedVerdict; }
    public String getFinalVerdict() { return finalVerdict; }
    public void setFinalVerdict(String finalVerdict) { this.finalVerdict = finalVerdict; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}