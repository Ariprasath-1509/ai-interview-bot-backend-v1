package com.benchreadiness.screening.dto;

import com.benchreadiness.screening.entity.enums.RoundDecision;
import jakarta.validation.constraints.NotNull;

/** Shared shape for both the Round 2 (recruiter) and Round 3 (manager) feedback forms. */
public class RoundFeedbackRequest {

    private String strengths;
    private String weaknesses;
    private String practical;
    private String improvements;

    @NotNull
    private RoundDecision result;

    /** Only required when result == SELECTED on Round 3 — used to satisfy auth-service's candidate-creation fields. */
    private ConversionDetails conversionDetails;

    public String getStrengths() { return strengths; }
    public void setStrengths(String strengths) { this.strengths = strengths; }
    public String getWeaknesses() { return weaknesses; }
    public void setWeaknesses(String weaknesses) { this.weaknesses = weaknesses; }
    public String getPractical() { return practical; }
    public void setPractical(String practical) { this.practical = practical; }
    public String getImprovements() { return improvements; }
    public void setImprovements(String improvements) { this.improvements = improvements; }
    public RoundDecision getResult() { return result; }
    public void setResult(RoundDecision result) { this.result = result; }
    public ConversionDetails getConversionDetails() { return conversionDetails; }
    public void setConversionDetails(ConversionDetails conversionDetails) { this.conversionDetails = conversionDetails; }

    public static class ConversionDetails {
        private String contactNumber;
        private String batchLabel;
        private String source;
        private String skillSet;

        public String getContactNumber() { return contactNumber; }
        public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }
        public String getBatchLabel() { return batchLabel; }
        public void setBatchLabel(String batchLabel) { this.batchLabel = batchLabel; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getSkillSet() { return skillSet; }
        public void setSkillSet(String skillSet) { this.skillSet = skillSet; }
    }
}
