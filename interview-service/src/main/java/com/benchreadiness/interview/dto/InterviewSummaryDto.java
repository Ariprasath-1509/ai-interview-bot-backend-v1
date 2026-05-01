package com.benchreadiness.interview.dto;

public class InterviewSummaryDto {
    private String id;
    private String status;
    private String proposedVerdict;
    private String finalVerdict;
    private String candidateName;
    private String candidateEmail;
    private String jdTitle;
    private String createdAt;
    private String interviewMode;
    
    // Constructors
    public InterviewSummaryDto() {}

    public InterviewSummaryDto(String id, String status, String proposedVerdict, String finalVerdict,
                                String candidateName, String candidateEmail, String jdTitle, String createdAt, String interviewMode) {
        this.id = id;
        this.status = status;
        this.proposedVerdict = proposedVerdict;
        this.finalVerdict = finalVerdict;
        this.candidateName = candidateName;
        this.candidateEmail = candidateEmail;
        this.jdTitle = jdTitle;
        this.createdAt = createdAt;
        this.interviewMode = interviewMode;
    }
    
    // Getters
    public String getId() { return id; }
    public String getStatus() { return status; }
    public String getProposedVerdict() { return proposedVerdict; }
    public String getFinalVerdict() { return finalVerdict; }
    public String getCandidateName() { return candidateName; }
    public String getCandidateEmail() { return candidateEmail; }
    public String getJdTitle() { return jdTitle; }
    public String getCreatedAt() { return createdAt; }
    public String getInterviewMode() { return interviewMode; }
    
    // Setters
    public void setId(String id) { this.id = id; }
    public void setStatus(String status) { this.status = status; }
    public void setProposedVerdict(String proposedVerdict) { this.proposedVerdict = proposedVerdict; }
    public void setFinalVerdict(String finalVerdict) { this.finalVerdict = finalVerdict; }
    public void setCandidateName(String candidateName) { this.candidateName = candidateName; }
    public void setCandidateEmail(String candidateEmail) { this.candidateEmail = candidateEmail; }
    public void setJdTitle(String jdTitle) { this.jdTitle = jdTitle; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public void setInterviewMode(String interviewMode) { this.interviewMode = interviewMode; }
}