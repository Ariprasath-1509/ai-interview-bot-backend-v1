package com.benchreadiness.screening.dto;

import com.benchreadiness.screening.entity.enums.RoundDecision;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Round 3 (Managerial) feedback — 6 scored categories (5 marks each, /30 total) plus concluding comments. */
public class Round3FeedbackRequest {

    @NotNull @Min(0) @Max(5)
    private Integer communication;

    @NotNull @Min(0) @Max(5)
    private Integer problemSolving;

    @NotNull @Min(0) @Max(5)
    private Integer attitudeCoachability;

    @NotNull @Min(0) @Max(5)
    private Integer learningAgility;

    @NotNull @Min(0) @Max(5)
    private Integer teamwork;

    @NotNull @Min(0) @Max(5)
    private Integer bodyLanguage;

    private String concludingComments;

    @NotNull
    private RoundDecision result;

    /** Only required when result == SELECTED — used to satisfy auth-service's candidate-creation fields. */
    private ConversionDetails conversionDetails;

    public Integer getCommunication() { return communication; }
    public void setCommunication(Integer communication) { this.communication = communication; }
    public Integer getProblemSolving() { return problemSolving; }
    public void setProblemSolving(Integer problemSolving) { this.problemSolving = problemSolving; }
    public Integer getAttitudeCoachability() { return attitudeCoachability; }
    public void setAttitudeCoachability(Integer attitudeCoachability) { this.attitudeCoachability = attitudeCoachability; }
    public Integer getLearningAgility() { return learningAgility; }
    public void setLearningAgility(Integer learningAgility) { this.learningAgility = learningAgility; }
    public Integer getTeamwork() { return teamwork; }
    public void setTeamwork(Integer teamwork) { this.teamwork = teamwork; }
    public Integer getBodyLanguage() { return bodyLanguage; }
    public void setBodyLanguage(Integer bodyLanguage) { this.bodyLanguage = bodyLanguage; }
    public String getConcludingComments() { return concludingComments; }
    public void setConcludingComments(String concludingComments) { this.concludingComments = concludingComments; }
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
