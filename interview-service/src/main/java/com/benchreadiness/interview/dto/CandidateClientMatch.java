package com.benchreadiness.interview.dto;

import java.time.Instant;
import java.util.List;

public class CandidateClientMatch {
    private String clientId;
    private String clientName;
    private String jdRole;
    private String jdDescription;
    private Double matchScore;
    private String recommendation;
    private List<String> strengths;
    private List<String> concerns;
    private String matchRationale;
    private Instant lastComputedAt;
    private String cacheSource; // "cached" or "ai-fresh"
    
    // Client requirements info
    private Integer benchB2bCandidatesNeeded;
    private Integer marketCandidatesNeeded;
    private String clientStatus;

    public CandidateClientMatch() {}

    public CandidateClientMatch(String clientId, String clientName, String jdRole, String jdDescription,
                               Double matchScore, String recommendation, List<String> strengths, 
                               List<String> concerns, String matchRationale, Instant lastComputedAt,
                               String cacheSource, Integer benchB2bCandidatesNeeded, 
                               Integer marketCandidatesNeeded, String clientStatus) {
        this.clientId = clientId;
        this.clientName = clientName;
        this.jdRole = jdRole;
        this.jdDescription = jdDescription;
        this.matchScore = matchScore;
        this.recommendation = recommendation;
        this.strengths = strengths;
        this.concerns = concerns;
        this.matchRationale = matchRationale;
        this.lastComputedAt = lastComputedAt;
        this.cacheSource = cacheSource;
        this.benchB2bCandidatesNeeded = benchB2bCandidatesNeeded;
        this.marketCandidatesNeeded = marketCandidatesNeeded;
        this.clientStatus = clientStatus;
    }

    // Getters and setters
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }

    public String getJdRole() { return jdRole; }
    public void setJdRole(String jdRole) { this.jdRole = jdRole; }

    public String getJdDescription() { return jdDescription; }
    public void setJdDescription(String jdDescription) { this.jdDescription = jdDescription; }

    public Double getMatchScore() { return matchScore; }
    public void setMatchScore(Double matchScore) { this.matchScore = matchScore; }

    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }

    public List<String> getStrengths() { return strengths; }
    public void setStrengths(List<String> strengths) { this.strengths = strengths; }

    public List<String> getConcerns() { return concerns; }
    public void setConcerns(List<String> concerns) { this.concerns = concerns; }

    public String getMatchRationale() { return matchRationale; }
    public void setMatchRationale(String matchRationale) { this.matchRationale = matchRationale; }

    public Instant getLastComputedAt() { return lastComputedAt; }
    public void setLastComputedAt(Instant lastComputedAt) { this.lastComputedAt = lastComputedAt; }

    public String getCacheSource() { return cacheSource; }
    public void setCacheSource(String cacheSource) { this.cacheSource = cacheSource; }

    public Integer getBenchB2bCandidatesNeeded() { return benchB2bCandidatesNeeded; }
    public void setBenchB2bCandidatesNeeded(Integer benchB2bCandidatesNeeded) { this.benchB2bCandidatesNeeded = benchB2bCandidatesNeeded; }

    public Integer getMarketCandidatesNeeded() { return marketCandidatesNeeded; }
    public void setMarketCandidatesNeeded(Integer marketCandidatesNeeded) { this.marketCandidatesNeeded = marketCandidatesNeeded; }

    public String getClientStatus() { return clientStatus; }
    public void setClientStatus(String clientStatus) { this.clientStatus = clientStatus; }
}