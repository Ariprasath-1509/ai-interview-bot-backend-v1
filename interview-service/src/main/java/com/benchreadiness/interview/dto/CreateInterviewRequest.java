package com.benchreadiness.interview.dto;

import com.benchreadiness.interview.entity.InterviewMode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public class CreateInterviewRequest {

    @NotBlank(message = "Engineer email is required")
    @Email(message = "Engineer email must be a valid email address")
    @Size(max = 255)
    private String engineerEmail;

    @Size(max = 255)
    private String engineerName;

    @NotBlank(message = "Job description title is required")
    @Size(max = 500)
    private String jdTitle;

    @Size(max = 100_000, message = "Job description text must not exceed 100 000 characters")
    private String jdText;
    private String focusAreas;
    private String resumeSummary;
    @NotNull(message = "Interview mode is required")
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

    // Optional: admin-selected question IDs from question bank (comma-separated UUIDs)
    private String selectedQuestionIds;
    
    // Optional: admin-provided custom questions (list of question texts)
    private java.util.List<String> customQuestions;

    /** Client-facing round label for reports (e.g. Hands-On, Technical Screen). */
    private String roundName;

    /** Per-round question difficulty override (EASY | MEDIUM | HARD); null derives from interviewMode. */
    private String questionDifficulty;

    /** CLIENT_INTERVIEW (default) or ONBOARDING. Onboarding reuses jdTitle/jdText as the concept/topic fields. */
    private String assessmentType;

    /** When false, interview is theory/verbal only (no coding slot or code editor). Defaults to true. */
    private Boolean includeProgrammingQuestions;

    /** ISO-8601 instant from which the candidate can access the interview (null = immediately). */
    private Instant scheduledAt;

    /** ISO-8601 instant after which the interview link is inaccessible (null = no expiry). */
    private Instant expiresAt;
    
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

    public String getRoundName() {
        return roundName;
    }

    public String getQuestionDifficulty() {
        return questionDifficulty;
    }

    public String getAssessmentType() {
        return assessmentType;
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

    public String getSelectedQuestionIds() {
        return selectedQuestionIds;
    }
    
    public java.util.List<String> getCustomQuestions() {
        return customQuestions;
    }

    public Boolean getIncludeProgrammingQuestions() {
        return includeProgrammingQuestions;
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

    public void setRoundName(String roundName) {
        this.roundName = roundName;
    }

    public void setQuestionDifficulty(String questionDifficulty) {
        this.questionDifficulty = questionDifficulty;
    }

    public void setAssessmentType(String assessmentType) {
        this.assessmentType = assessmentType;
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

    public void setSelectedQuestionIds(String selectedQuestionIds) {
        this.selectedQuestionIds = selectedQuestionIds;
    }
    
    public void setCustomQuestions(java.util.List<String> customQuestions) {
        this.customQuestions = customQuestions;
    }

    public void setIncludeProgrammingQuestions(Boolean includeProgrammingQuestions) {
        this.includeProgrammingQuestions = includeProgrammingQuestions;
    }

    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}