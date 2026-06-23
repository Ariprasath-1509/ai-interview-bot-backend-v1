package com.benchreadiness.interview.dto;

import java.time.LocalDateTime;

public class ClientMatchingOverview {
    private String clientId;
    private String clientName;
    private String jdRole;
    private Integer positionsVacant;
    private Integer benchB2bCandidatesNeeded;
    private Integer marketCandidatesNeeded;
    private String status;
    private MatchingSummary benchB2bSummary;
    private MatchingSummary marketSummary;
    private LocalDateTime createdAt;

    // Nested class for matching summary
    public static class MatchingSummary {
        private Integer totalMatches;
        private Integer highlyRecommended;
        private Integer recommended;
        private Integer consider;
        private Integer notSuitable;
        private LocalDateTime lastComputedAt;
        private boolean cached;

        public MatchingSummary() {}

        public MatchingSummary(Integer totalMatches, Integer highlyRecommended, 
                              Integer recommended, Integer consider, Integer notSuitable,
                              LocalDateTime lastComputedAt, boolean cached) {
            this.totalMatches = totalMatches;
            this.highlyRecommended = highlyRecommended;
            this.recommended = recommended;
            this.consider = consider;
            this.notSuitable = notSuitable;
            this.lastComputedAt = lastComputedAt;
            this.cached = cached;
        }

        // Getters and Setters
        public Integer getTotalMatches() { return totalMatches; }
        public void setTotalMatches(Integer totalMatches) { this.totalMatches = totalMatches; }

        public Integer getHighlyRecommended() { return highlyRecommended; }
        public void setHighlyRecommended(Integer highlyRecommended) { this.highlyRecommended = highlyRecommended; }

        public Integer getRecommended() { return recommended; }
        public void setRecommended(Integer recommended) { this.recommended = recommended; }

        public Integer getConsider() { return consider; }
        public void setConsider(Integer consider) { this.consider = consider; }

        public Integer getNotSuitable() { return notSuitable; }
        public void setNotSuitable(Integer notSuitable) { this.notSuitable = notSuitable; }

        public LocalDateTime getLastComputedAt() { return lastComputedAt; }
        public void setLastComputedAt(LocalDateTime lastComputedAt) { this.lastComputedAt = lastComputedAt; }

        public boolean isCached() { return cached; }
        public void setCached(boolean cached) { this.cached = cached; }
    }

    // Constructors
    public ClientMatchingOverview() {}

    // Getters and Setters
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }

    public String getJdRole() { return jdRole; }
    public void setJdRole(String jdRole) { this.jdRole = jdRole; }

    public Integer getPositionsVacant() { return positionsVacant; }
    public void setPositionsVacant(Integer positionsVacant) { this.positionsVacant = positionsVacant; }

    public Integer getBenchB2bCandidatesNeeded() { return benchB2bCandidatesNeeded; }
    public void setBenchB2bCandidatesNeeded(Integer benchB2bCandidatesNeeded) { 
        this.benchB2bCandidatesNeeded = benchB2bCandidatesNeeded; 
    }

    public Integer getMarketCandidatesNeeded() { return marketCandidatesNeeded; }
    public void setMarketCandidatesNeeded(Integer marketCandidatesNeeded) { 
        this.marketCandidatesNeeded = marketCandidatesNeeded; 
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public MatchingSummary getBenchB2bSummary() { return benchB2bSummary; }
    public void setBenchB2bSummary(MatchingSummary benchB2bSummary) { 
        this.benchB2bSummary = benchB2bSummary; 
    }

    public MatchingSummary getMarketSummary() { return marketSummary; }
    public void setMarketSummary(MatchingSummary marketSummary) { 
        this.marketSummary = marketSummary; 
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
