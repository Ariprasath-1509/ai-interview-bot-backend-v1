package com.benchreadiness.auth.dto;

import com.benchreadiness.auth.entity.CandidateRating;
import com.benchreadiness.auth.entity.CandidateStatus;

public class UpdateCandidateRequest {

    private CandidateRating rating;
    private CandidateStatus candidateStatus;
    private Integer noOfInterviews;

    public CandidateRating getRating() { return rating; }
    public void setRating(CandidateRating rating) { this.rating = rating; }
    public CandidateStatus getCandidateStatus() { return candidateStatus; }
    public void setCandidateStatus(CandidateStatus candidateStatus) { this.candidateStatus = candidateStatus; }
    public Integer getNoOfInterviews() { return noOfInterviews; }
    public void setNoOfInterviews(Integer noOfInterviews) { this.noOfInterviews = noOfInterviews; }
}
