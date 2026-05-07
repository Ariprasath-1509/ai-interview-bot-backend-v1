package com.benchreadiness.auth.dto;

import com.benchreadiness.auth.entity.CandidateRating;
import com.benchreadiness.auth.entity.CandidateSource;
import com.benchreadiness.auth.entity.CandidateStatus;
import com.benchreadiness.auth.entity.SkillSet;
import java.math.BigDecimal;

public class UpdateCandidateRequest {

    private String name;
    private String email;
    private String officialEmail;
    private String personalEmail;
    private String contactNumber;
    private String batch;
    private String batchMentor;
    private CandidateSource source;
    private CandidateStatus candidateStatus;
    private CandidateRating rating;
    private SkillSet skillSet;
    private BigDecimal yoeActual;
    private BigDecimal yoePortrayed;
    private Integer yop;
    private Integer noOfInterviews;
    private String interviewMentorName;
    private String clientName;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
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
    public CandidateSource getSource() { return source; }
    public void setSource(CandidateSource source) { this.source = source; }
    public CandidateStatus getCandidateStatus() { return candidateStatus; }
    public void setCandidateStatus(CandidateStatus candidateStatus) { this.candidateStatus = candidateStatus; }
    public CandidateRating getRating() { return rating; }
    public void setRating(CandidateRating rating) { this.rating = rating; }
    public SkillSet getSkillSet() { return skillSet; }
    public void setSkillSet(SkillSet skillSet) { this.skillSet = skillSet; }
    public BigDecimal getYoeActual() { return yoeActual; }
    public void setYoeActual(BigDecimal yoeActual) { this.yoeActual = yoeActual; }
    public BigDecimal getYoePortrayed() { return yoePortrayed; }
    public void setYoePortrayed(BigDecimal yoePortrayed) { this.yoePortrayed = yoePortrayed; }
    public Integer getYop() { return yop; }
    public void setYop(Integer yop) { this.yop = yop; }
    public Integer getNoOfInterviews() { return noOfInterviews; }
    public void setNoOfInterviews(Integer noOfInterviews) { this.noOfInterviews = noOfInterviews; }
    public String getInterviewMentorName() { return interviewMentorName; }
    public void setInterviewMentorName(String interviewMentorName) { this.interviewMentorName = interviewMentorName; }
    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }
}
