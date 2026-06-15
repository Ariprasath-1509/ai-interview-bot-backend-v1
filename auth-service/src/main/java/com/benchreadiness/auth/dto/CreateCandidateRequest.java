package com.benchreadiness.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateCandidateRequest {

    @NotBlank
    private String name;

    private String officialEmail;

    private String personalEmail;

    @NotBlank
    private String contactNumber;

    @NotBlank
    private String batch;

    private String batchMentor;

    @NotBlank
    private String source;

    private String candidateStatus;

    private String rating;

    @NotBlank
    private String skillSet;

    private Double yoeActual;

    private Double yoePortrayed;

    private Integer yop;

    private Integer noOfInterviews;

    private String interviewMentorName;

    private String clientName;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOfficialEmail() { return officialEmail; }
    public void setOfficialEmail(String officialEmail) { this.officialEmail = officialEmail; }
    public String getPersonalEmail() { return personalEmail; }
    public void setPersonalEmail(String personalEmail) { this.personalEmail = personalEmail; }
    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }
    public String getBatch() { return batch; }
    public void setBatch(String batch) { this.batch = batch; }
    public String getBatchMentor() { return batchMentor; }
    public void setBatchMentor(String batchMentor) { this.batchMentor = batchMentor; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getCandidateStatus() { return candidateStatus; }
    public void setCandidateStatus(String candidateStatus) { this.candidateStatus = candidateStatus; }
    public String getRating() { return rating; }
    public void setRating(String rating) { this.rating = rating; }
    public String getSkillSet() { return skillSet; }
    public void setSkillSet(String skillSet) { this.skillSet = skillSet; }
    public Double getYoeActual() { return yoeActual; }
    public void setYoeActual(Double yoeActual) { this.yoeActual = yoeActual; }
    public Double getYoePortrayed() { return yoePortrayed; }
    public void setYoePortrayed(Double yoePortrayed) { this.yoePortrayed = yoePortrayed; }
    public Integer getYop() { return yop; }
    public void setYop(Integer yop) { this.yop = yop; }
    public Integer getNoOfInterviews() { return noOfInterviews; }
    public void setNoOfInterviews(Integer noOfInterviews) { this.noOfInterviews = noOfInterviews; }
    public String getInterviewMentorName() { return interviewMentorName; }
    public void setInterviewMentorName(String interviewMentorName) { this.interviewMentorName = interviewMentorName; }
    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }
}
