package com.benchreadiness.interview.dto;

import com.benchreadiness.interview.entity.InterviewMode;

public class AutoFillPreview {
    private String engineerEmail;
    private String engineerName;
    private String jdTitle;
    private String jdText;
    private String focusAreas;
    private String resumeSummary;
    private InterviewMode suggestedMode;
    private String notes;
    private String clientName;
    private boolean candidateDataFound;
    private boolean clientDataFound;

    public AutoFillPreview() {}

    public AutoFillPreview(String engineerEmail, String engineerName, String jdTitle, String jdText, 
                          String focusAreas, String resumeSummary, InterviewMode suggestedMode, 
                          String notes, String clientName, boolean candidateDataFound, boolean clientDataFound) {
        this.engineerEmail = engineerEmail;
        this.engineerName = engineerName;
        this.jdTitle = jdTitle;
        this.jdText = jdText;
        this.focusAreas = focusAreas;
        this.resumeSummary = resumeSummary;
        this.suggestedMode = suggestedMode;
        this.notes = notes;
        this.clientName = clientName;
        this.candidateDataFound = candidateDataFound;
        this.clientDataFound = clientDataFound;
    }

    public String getEngineerEmail() {
        return engineerEmail;
    }

    public void setEngineerEmail(String engineerEmail) {
        this.engineerEmail = engineerEmail;
    }

    public String getEngineerName() {
        return engineerName;
    }

    public void setEngineerName(String engineerName) {
        this.engineerName = engineerName;
    }

    public String getJdTitle() {
        return jdTitle;
    }

    public void setJdTitle(String jdTitle) {
        this.jdTitle = jdTitle;
    }

    public String getJdText() {
        return jdText;
    }

    public void setJdText(String jdText) {
        this.jdText = jdText;
    }

    public String getFocusAreas() {
        return focusAreas;
    }

    public void setFocusAreas(String focusAreas) {
        this.focusAreas = focusAreas;
    }

    public String getResumeSummary() {
        return resumeSummary;
    }

    public void setResumeSummary(String resumeSummary) {
        this.resumeSummary = resumeSummary;
    }

    public InterviewMode getSuggestedMode() {
        return suggestedMode;
    }

    public void setSuggestedMode(InterviewMode suggestedMode) {
        this.suggestedMode = suggestedMode;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public boolean isCandidateDataFound() {
        return candidateDataFound;
    }

    public void setCandidateDataFound(boolean candidateDataFound) {
        this.candidateDataFound = candidateDataFound;
    }

    public boolean isClientDataFound() {
        return clientDataFound;
    }

    public void setClientDataFound(boolean clientDataFound) {
        this.clientDataFound = clientDataFound;
    }
}