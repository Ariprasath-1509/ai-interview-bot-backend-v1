package com.benchreadiness.interview.dto;

import java.util.List;

public class CandidateMatch {
    private String candidateId;
    private String candidateName;
    private String candidateEmail;
    private String skillSet;
    private Double yoeActual;
    private String rating;
    private String candidateStatus;
    private Integer noOfInterviews;
    private Double matchScore;
    private String matchRationale;
    private List<String> strengths;
    private List<String> concerns;
    private String lastInterviewDate;
    private String lastVerdict;
    private Double avgScore;

    public CandidateMatch() {}

    public CandidateMatch(String candidateId, String candidateName, String candidateEmail, 
                         String skillSet, Double yoeActual, String rating, String candidateStatus, 
                         Integer noOfInterviews, Double matchScore, String matchRationale, 
                         List<String> strengths, List<String> concerns, String lastInterviewDate, 
                         String lastVerdict, Double avgScore) {
        this.candidateId = candidateId;
        this.candidateName = candidateName;
        this.candidateEmail = candidateEmail;
        this.skillSet = skillSet;
        this.yoeActual = yoeActual;
        this.rating = rating;
        this.candidateStatus = candidateStatus;
        this.noOfInterviews = noOfInterviews;
        this.matchScore = matchScore;
        this.matchRationale = matchRationale;
        this.strengths = strengths;
        this.concerns = concerns;
        this.lastInterviewDate = lastInterviewDate;
        this.lastVerdict = lastVerdict;
        this.avgScore = avgScore;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(String candidateId) {
        this.candidateId = candidateId;
    }

    public String getCandidateName() {
        return candidateName;
    }

    public void setCandidateName(String candidateName) {
        this.candidateName = candidateName;
    }

    public String getCandidateEmail() {
        return candidateEmail;
    }

    public void setCandidateEmail(String candidateEmail) {
        this.candidateEmail = candidateEmail;
    }

    public String getSkillSet() {
        return skillSet;
    }

    public void setSkillSet(String skillSet) {
        this.skillSet = skillSet;
    }

    public Double getYoeActual() {
        return yoeActual;
    }

    public void setYoeActual(Double yoeActual) {
        this.yoeActual = yoeActual;
    }

    public String getRating() {
        return rating;
    }

    public void setRating(String rating) {
        this.rating = rating;
    }

    public String getCandidateStatus() {
        return candidateStatus;
    }

    public void setCandidateStatus(String candidateStatus) {
        this.candidateStatus = candidateStatus;
    }

    public Integer getNoOfInterviews() {
        return noOfInterviews;
    }

    public void setNoOfInterviews(Integer noOfInterviews) {
        this.noOfInterviews = noOfInterviews;
    }

    public Double getMatchScore() {
        return matchScore;
    }

    public void setMatchScore(Double matchScore) {
        this.matchScore = matchScore;
    }

    public String getMatchRationale() {
        return matchRationale;
    }

    public void setMatchRationale(String matchRationale) {
        this.matchRationale = matchRationale;
    }

    public List<String> getStrengths() {
        return strengths;
    }

    public void setStrengths(List<String> strengths) {
        this.strengths = strengths;
    }

    public List<String> getConcerns() {
        return concerns;
    }

    public void setConcerns(List<String> concerns) {
        this.concerns = concerns;
    }

    public String getLastInterviewDate() {
        return lastInterviewDate;
    }

    public void setLastInterviewDate(String lastInterviewDate) {
        this.lastInterviewDate = lastInterviewDate;
    }

    public String getLastVerdict() {
        return lastVerdict;
    }

    public void setLastVerdict(String lastVerdict) {
        this.lastVerdict = lastVerdict;
    }

    public Double getAvgScore() {
        return avgScore;
    }

    public void setAvgScore(Double avgScore) {
        this.avgScore = avgScore;
    }
}