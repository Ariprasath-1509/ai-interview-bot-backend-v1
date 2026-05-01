package com.benchreadiness.interview.dto;

import com.benchreadiness.interview.entity.InterviewMode;

public class CreateInterviewRequest {
    
    // Existing fields
    private String engineerEmail;
    private String engineerName;
    private String jdTitle;
    private String jdText;
    private String focusAreas;
    private String resumeSummary;
    private InterviewMode interviewMode;
    private Integer customDurationMinutes;
    
    // New fields for AI matching
    private String candidateId; // Optional - for AI matching
    private String clientId; // Optional - for client-based matching
    private Long recruiterId;
    private Long jobPositionId; // Optional - will auto-fill if null
    private String interviewType;
    private String scheduledDate;
    private String notes;
    
    // Constructors
    public CreateInterviewRequest() {}
    
    public CreateInterviewRequest(String engineerEmail, String engineerName, String jdTitle, String jdText, 
                                 String focusAreas, String resumeSummary, InterviewMode interviewMode, 
                                 Integer customDurationMinutes, String candidateId, String clientId, Long recruiterId, 
                                 Long jobPositionId, String interviewType, String scheduledDate, String notes) {
        this.engineerEmail = engineerEmail;
        this.engineerName = engineerName;
        this.jdTitle = jdTitle;
        this.jdText = jdText;
        this.focusAreas = focusAreas;
        this.resumeSummary = resumeSummary;
        this.interviewMode = interviewMode;
        this.customDurationMinutes = customDurationMinutes;
        this.candidateId = candidateId;
        this.clientId = clientId;
        this.recruiterId = recruiterId;
        this.jobPositionId = jobPositionId;
        this.interviewType = interviewType;
        this.scheduledDate = scheduledDate;
        this.notes = notes;
    }
    
    // Getters
    public String getEngineerEmail() {
        return engineerEmail;
    }
    
    public String getEngineerName() {
        return engineerName;
    }
    
    public String getJdTitle() {
        return jdTitle;
    }
    
    public String getJdText() {
        return jdText;
    }
    
    public String getFocusAreas() {
        return focusAreas;
    }
    
    public String getResumeSummary() {
        return resumeSummary;
    }
    
    public InterviewMode getInterviewMode() {
        return interviewMode;
    }
    
    public Integer getCustomDurationMinutes() {
        return customDurationMinutes;
    }
    
    public String getCandidateId() {
        return candidateId;
    }
    
    public String getClientId() {
        return clientId;
    }
    
    public Long getRecruiterId() {
        return recruiterId;
    }
    
    public Long getJobPositionId() {
        return jobPositionId;
    }
    
    public String getInterviewType() {
        return interviewType;
    }
    
    public String getScheduledDate() {
        return scheduledDate;
    }
    
    public String getNotes() {
        return notes;
    }
    
    // Setters
    public void setEngineerEmail(String engineerEmail) {
        this.engineerEmail = engineerEmail;
    }
    
    public void setEngineerName(String engineerName) {
        this.engineerName = engineerName;
    }
    
    public void setJdTitle(String jdTitle) {
        this.jdTitle = jdTitle;
    }
    
    public void setJdText(String jdText) {
        this.jdText = jdText;
    }
    
    public void setFocusAreas(String focusAreas) {
        this.focusAreas = focusAreas;
    }
    
    public void setResumeSummary(String resumeSummary) {
        this.resumeSummary = resumeSummary;
    }
    
    public void setInterviewMode(InterviewMode interviewMode) {
        this.interviewMode = interviewMode;
    }
    
    public void setCustomDurationMinutes(Integer customDurationMinutes) {
        this.customDurationMinutes = customDurationMinutes;
    }
    
    public void setCandidateId(String candidateId) {
        this.candidateId = candidateId;
    }
    
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
    
    public void setRecruiterId(Long recruiterId) {
        this.recruiterId = recruiterId;
    }
    
    public void setJobPositionId(Long jobPositionId) {
        this.jobPositionId = jobPositionId;
    }
    
    public void setInterviewType(String interviewType) {
        this.interviewType = interviewType;
    }
    
    public void setScheduledDate(String scheduledDate) {
        this.scheduledDate = scheduledDate;
    }
    
    public void setNotes(String notes) {
        this.notes = notes;
    }
}