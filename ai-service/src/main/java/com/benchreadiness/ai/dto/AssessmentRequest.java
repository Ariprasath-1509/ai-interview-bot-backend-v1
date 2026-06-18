package com.benchreadiness.ai.dto;

public class AssessmentRequest {
    private String jdTitle;
    private String jdText;
    private String resumeSummary;
    private String candidateLabel;
    private String transcriptJson;
    private String rubricJson;
    private String candidateProfileJson;
    private String interviewMode;
    private String interviewId;
    private String codeSubmissionJson;
    /** When true, skip in-memory assessment cache (re-assessment). */
    private boolean forceRefresh;

    public String getJdTitle() { return jdTitle; }
    public void setJdTitle(String jdTitle) { this.jdTitle = jdTitle; }
    public String getJdText() { return jdText; }
    public void setJdText(String jdText) { this.jdText = jdText; }
    public String getResumeSummary() { return resumeSummary; }
    public void setResumeSummary(String resumeSummary) { this.resumeSummary = resumeSummary; }
    public String getCandidateLabel() { return candidateLabel; }
    public void setCandidateLabel(String candidateLabel) { this.candidateLabel = candidateLabel; }
    public String getTranscriptJson() { return transcriptJson; }
    public void setTranscriptJson(String transcriptJson) { this.transcriptJson = transcriptJson; }
    public String getRubricJson() { return rubricJson; }
    public void setRubricJson(String rubricJson) { this.rubricJson = rubricJson; }
    public String getCandidateProfileJson() { return candidateProfileJson; }
    public void setCandidateProfileJson(String candidateProfileJson) { this.candidateProfileJson = candidateProfileJson; }
    public String getInterviewMode() { return interviewMode; }
    public void setInterviewMode(String interviewMode) { this.interviewMode = interviewMode; }
    public String getInterviewId() { return interviewId; }
    public void setInterviewId(String interviewId) { this.interviewId = interviewId; }
    public String getCodeSubmissionJson() { return codeSubmissionJson; }
    public void setCodeSubmissionJson(String codeSubmissionJson) { this.codeSubmissionJson = codeSubmissionJson; }
    public boolean isForceRefresh() { return forceRefresh; }
    public void setForceRefresh(boolean forceRefresh) { this.forceRefresh = forceRefresh; }
}
