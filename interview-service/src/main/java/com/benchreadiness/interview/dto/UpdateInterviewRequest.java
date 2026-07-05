package com.benchreadiness.interview.dto;

import com.benchreadiness.interview.entity.InterviewMode;
import java.time.Instant;
import java.util.List;

public class UpdateInterviewRequest {

    private String jdTitle;
    private String jdText;
    private String focusAreas;
    private InterviewMode interviewMode;
    private Integer customDurationMinutes;
    private String roundName;
    /** EASY | MEDIUM | HARD; null leaves the stored value unchanged. */
    private String questionDifficulty;
    private Boolean includeProgrammingQuestions;
    private Instant scheduledAt;
    private Instant expiresAt;
    private String selectedQuestionIds;
    private List<String> customQuestions;

    public String getJdTitle() { return jdTitle; }
    public void setJdTitle(String jdTitle) { this.jdTitle = jdTitle; }
    public String getJdText() { return jdText; }
    public void setJdText(String jdText) { this.jdText = jdText; }
    public String getFocusAreas() { return focusAreas; }
    public void setFocusAreas(String focusAreas) { this.focusAreas = focusAreas; }
    public InterviewMode getInterviewMode() { return interviewMode; }
    public void setInterviewMode(InterviewMode interviewMode) { this.interviewMode = interviewMode; }
    public Integer getCustomDurationMinutes() { return customDurationMinutes; }
    public void setCustomDurationMinutes(Integer customDurationMinutes) { this.customDurationMinutes = customDurationMinutes; }
    public String getRoundName() { return roundName; }
    public void setRoundName(String roundName) { this.roundName = roundName; }
    public String getQuestionDifficulty() { return questionDifficulty; }
    public void setQuestionDifficulty(String questionDifficulty) { this.questionDifficulty = questionDifficulty; }
    public Boolean getIncludeProgrammingQuestions() { return includeProgrammingQuestions; }
    public void setIncludeProgrammingQuestions(Boolean includeProgrammingQuestions) { this.includeProgrammingQuestions = includeProgrammingQuestions; }
    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public String getSelectedQuestionIds() { return selectedQuestionIds; }
    public void setSelectedQuestionIds(String selectedQuestionIds) { this.selectedQuestionIds = selectedQuestionIds; }
    public List<String> getCustomQuestions() { return customQuestions; }
    public void setCustomQuestions(List<String> customQuestions) { this.customQuestions = customQuestions; }
}
