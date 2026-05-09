package com.benchreadiness.interview.dto;

import java.time.Instant;
import java.util.List;

public class CandidateMatchingResult {
    private String candidateId;
    private String candidateName;
    private String candidateEmail;
    private String skillSet;
    private Double yoeActual;
    private String rating;
    private String candidateStatus;
    private Integer systemInterviewCount;
    private List<CandidateClientMatch> matches;
    private Integer totalClientsAnalyzed;
    private Integer matchingClientsCount;
    private Double averageMatchScore;
    private Instant computedAt;
    private String cacheSource;

    public CandidateMatchingResult() {}

    public CandidateMatchingResult(String candidateId, String candidateName, String candidateEmail,
                                  String skillSet, Double yoeActual, String rating, String candidateStatus,
                                  Integer systemInterviewCount, List<CandidateClientMatch> matches,
                                  Integer totalClientsAnalyzed, Integer matchingClientsCount,
                                  Double averageMatchScore, Instant computedAt, String cacheSource) {
        this.candidateId = candidateId;
        this.candidateName = candidateName;
        this.candidateEmail = candidateEmail;
        this.skillSet = skillSet;
        this.yoeActual = yoeActual;
        this.rating = rating;
        this.candidateStatus = candidateStatus;
        this.systemInterviewCount = systemInterviewCount;
        this.matches = matches;
        this.totalClientsAnalyzed = totalClientsAnalyzed;
        this.matchingClientsCount = matchingClientsCount;
        this.averageMatchScore = averageMatchScore;
        this.computedAt = computedAt;
        this.cacheSource = cacheSource;
    }

    // Getters and setters
    public String getCandidateId() { return candidateId; }
    public void setCandidateId(String candidateId) { this.candidateId = candidateId; }

    public String getCandidateName() { return candidateName; }
    public void setCandidateName(String candidateName) { this.candidateName = candidateName; }

    public String getCandidateEmail() { return candidateEmail; }
    public void setCandidateEmail(String candidateEmail) { this.candidateEmail = candidateEmail; }

    public String getSkillSet() { return skillSet; }
    public void setSkillSet(String skillSet) { this.skillSet = skillSet; }

    public Double getYoeActual() { return yoeActual; }
    public void setYoeActual(Double yoeActual) { this.yoeActual = yoeActual; }

    public String getRating() { return rating; }
    public void setRating(String rating) { this.rating = rating; }

    public String getCandidateStatus() { return candidateStatus; }
    public void setCandidateStatus(String candidateStatus) { this.candidateStatus = candidateStatus; }

    public Integer getSystemInterviewCount() { return systemInterviewCount; }
    public void setSystemInterviewCount(Integer systemInterviewCount) { this.systemInterviewCount = systemInterviewCount; }

    public List<CandidateClientMatch> getMatches() { return matches; }
    public void setMatches(List<CandidateClientMatch> matches) { this.matches = matches; }

    public Integer getTotalClientsAnalyzed() { return totalClientsAnalyzed; }
    public void setTotalClientsAnalyzed(Integer totalClientsAnalyzed) { this.totalClientsAnalyzed = totalClientsAnalyzed; }

    public Integer getMatchingClientsCount() { return matchingClientsCount; }
    public void setMatchingClientsCount(Integer matchingClientsCount) { this.matchingClientsCount = matchingClientsCount; }

    public Double getAverageMatchScore() { return averageMatchScore; }
    public void setAverageMatchScore(Double averageMatchScore) { this.averageMatchScore = averageMatchScore; }

    public Instant getComputedAt() { return computedAt; }
    public void setComputedAt(Instant computedAt) { this.computedAt = computedAt; }

    public String getCacheSource() { return cacheSource; }
    public void setCacheSource(String cacheSource) { this.cacheSource = cacheSource; }
}