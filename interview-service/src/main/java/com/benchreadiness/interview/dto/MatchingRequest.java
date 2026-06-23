package com.benchreadiness.interview.dto;

import jakarta.validation.constraints.NotBlank;

public class MatchingRequest {
    @NotBlank
    private String clientId;
    
    private Integer maxCandidates = 10;
    private String source; // "BENCH_B2B" or "MARKET"
    private String skillSet; // Optional: filter by specific skill
    private Double minYoeRequired; // Optional: filter by specific YOE requirement

    public MatchingRequest() {}

    public MatchingRequest(String clientId, Integer maxCandidates, String source) {
        this.clientId = clientId;
        this.maxCandidates = maxCandidates;
        this.source = source;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public Integer getMaxCandidates() {
        return maxCandidates;
    }

    public void setMaxCandidates(Integer maxCandidates) {
        this.maxCandidates = maxCandidates;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSkillSet() {
        return skillSet;
    }

    public void setSkillSet(String skillSet) {
        this.skillSet = skillSet;
    }

    public Double getMinYoeRequired() {
        return minYoeRequired;
    }

    public void setMinYoeRequired(Double minYoeRequired) {
        this.minYoeRequired = minYoeRequired;
    }
}