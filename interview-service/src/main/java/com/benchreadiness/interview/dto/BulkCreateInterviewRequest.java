package com.benchreadiness.interview.dto;

import com.benchreadiness.interview.entity.InterviewMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public class BulkCreateInterviewRequest {

    /** Fields shared across every interview in the batch. */
    @NotBlank(message = "JD title is required")
    @Size(max = 500)
    private String jdTitle;

    @Size(max = 100_000)
    private String jdText;

    private String focusAreas;

    @NotNull(message = "Interview mode is required")
    private InterviewMode interviewMode;

    private Integer customDurationMinutes;

    /** When false, no coding slot for any interview. Defaults to true. */
    private Boolean includeProgrammingQuestions;

    /** ISO-8601 — same scheduled time for all candidates (null = immediately). */
    private Instant scheduledAt;

    /** ISO-8601 — same expiry for all candidates (null = no expiry). */
    private Instant expiresAt;

    /** Optional: round label e.g. "Hands-On", "Technical Screen". */
    @Size(max = 100)
    private String roundName;

    /** Per-candidate entries. Max 20 per request. */
    @NotEmpty(message = "At least one candidate is required")
    @Valid
    private List<CandidateEntry> candidates;

    // ── Inner class ──────────────────────────────────────────────────────────

    public static class CandidateEntry {

        @NotBlank(message = "Candidate email is required")
        @Size(max = 255)
        private String engineerEmail;

        @Size(max = 255)
        private String engineerName;

        /** Optional resume summary to include in the AI rubric prompt. */
        private String resumeSummary;

        /** UUID string — optional per-candidate client override. */
        private String clientId;

        public String getEngineerEmail() { return engineerEmail; }
        public void setEngineerEmail(String engineerEmail) { this.engineerEmail = engineerEmail; }
        public String getEngineerName() { return engineerName; }
        public void setEngineerName(String engineerName) { this.engineerName = engineerName; }
        public String getResumeSummary() { return resumeSummary; }
        public void setResumeSummary(String resumeSummary) { this.resumeSummary = resumeSummary; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

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
    public Boolean getIncludeProgrammingQuestions() { return includeProgrammingQuestions; }
    public void setIncludeProgrammingQuestions(Boolean includeProgrammingQuestions) { this.includeProgrammingQuestions = includeProgrammingQuestions; }
    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public String getRoundName() { return roundName; }
    public void setRoundName(String roundName) { this.roundName = roundName; }
    public List<CandidateEntry> getCandidates() { return candidates; }
    public void setCandidates(List<CandidateEntry> candidates) { this.candidates = candidates; }
}
