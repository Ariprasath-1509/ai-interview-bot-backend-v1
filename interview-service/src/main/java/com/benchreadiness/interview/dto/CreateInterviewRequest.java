package com.benchreadiness.interview.dto;

import com.benchreadiness.interview.entity.InterviewMode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateInterviewRequest {

    @NotBlank @Email
    private String engineerEmail;

    private String engineerName;

    @NotBlank
    private String jdTitle;

    @NotBlank @Size(min = 5, message = "JD text must be at least 50 characters")
    private String jdText;

    private String focusAreas;

    @NotBlank(message = "Resume summary is required for accurate evaluation")
    private String resumeSummary;

    private InterviewMode interviewMode = InterviewMode.SCREENING;
    
    private Integer customDurationMinutes; // Optional override for mode default

    public String getEngineerEmail() { return engineerEmail; }
    public void setEngineerEmail(String engineerEmail) { this.engineerEmail = engineerEmail; }
    public String getEngineerName() { return engineerName; }
    public void setEngineerName(String engineerName) { this.engineerName = engineerName; }
    public String getJdTitle() { return jdTitle; }
    public void setJdTitle(String jdTitle) { this.jdTitle = jdTitle; }
    public String getJdText() { return jdText; }
    public void setJdText(String jdText) { this.jdText = jdText; }
    public String getFocusAreas() { return focusAreas; }
    public void setFocusAreas(String focusAreas) { this.focusAreas = focusAreas; }
    public String getResumeSummary() { return resumeSummary; }
    public void setResumeSummary(String resumeSummary) { this.resumeSummary = resumeSummary; }
    public InterviewMode getInterviewMode() { return interviewMode; }
    public void setInterviewMode(InterviewMode interviewMode) { this.interviewMode = interviewMode; }
    public Integer getCustomDurationMinutes() { return customDurationMinutes; }
    public void setCustomDurationMinutes(Integer customDurationMinutes) { this.customDurationMinutes = customDurationMinutes; }
}
