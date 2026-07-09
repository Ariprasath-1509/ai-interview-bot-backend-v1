package com.benchreadiness.screening.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public class CreateBatchFromDocumentRequest {

    public enum Mode { JD, QUESTION_PAPER }

    @NotNull
    private Mode mode;

    @NotBlank
    private String documentText;

    /** Free-text label for the batches list/report — not validated against the fixed language preset list. */
    @NotBlank
    private String language;

    @NotNull
    private Instant deadline;

    @NotEmpty
    @Valid
    private List<CreateBatchRequest.CandidateEntry> candidates;

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }
    public String getDocumentText() { return documentText; }
    public void setDocumentText(String documentText) { this.documentText = documentText; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public Instant getDeadline() { return deadline; }
    public void setDeadline(Instant deadline) { this.deadline = deadline; }
    public List<CreateBatchRequest.CandidateEntry> getCandidates() { return candidates; }
    public void setCandidates(List<CreateBatchRequest.CandidateEntry> candidates) { this.candidates = candidates; }
}
