package com.benchreadiness.interview.dto;

import com.benchreadiness.interview.entity.SkillSet;
import java.util.List;

public class SkillBasedMatchingResult {
    
    private String clientId;
    private String clientName;
    private String source;
    private List<SkillRequirementMatch> skillRequirements;
    private MatchingSummary summary;
    private String computedAt;
    private String cacheSource;
    
    // Constructors
    public SkillBasedMatchingResult() {}
    
    // Getters and Setters
    public String getClientId() {
        return clientId;
    }
    
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
    
    public String getClientName() {
        return clientName;
    }
    
    public void setClientName(String clientName) {
        this.clientName = clientName;
    }
    
    public String getSource() {
        return source;
    }
    
    public void setSource(String source) {
        this.source = source;
    }
    
    public List<SkillRequirementMatch> getSkillRequirements() {
        return skillRequirements;
    }
    
    public void setSkillRequirements(List<SkillRequirementMatch> skillRequirements) {
        this.skillRequirements = skillRequirements;
    }
    
    public MatchingSummary getSummary() {
        return summary;
    }
    
    public void setSummary(MatchingSummary summary) {
        this.summary = summary;
    }
    
    public String getComputedAt() {
        return computedAt;
    }
    
    public void setComputedAt(String computedAt) {
        this.computedAt = computedAt;
    }
    
    public String getCacheSource() {
        return cacheSource;
    }
    
    public void setCacheSource(String cacheSource) {
        this.cacheSource = cacheSource;
    }
    
    public static class SkillRequirementMatch {
        private SkillSet skillSet;
        private List<PositionMatch> positions;
        
        // Constructors
        public SkillRequirementMatch() {}
        
        public SkillRequirementMatch(SkillSet skillSet) {
            this.skillSet = skillSet;
        }
        
        // Getters and Setters
        public SkillSet getSkillSet() {
            return skillSet;
        }
        
        public void setSkillSet(SkillSet skillSet) {
            this.skillSet = skillSet;
        }
        
        public List<PositionMatch> getPositions() {
            return positions;
        }
        
        public void setPositions(List<PositionMatch> positions) {
            this.positions = positions;
        }
    }
    
    public static class PositionMatch {
        private Double minYoeRequired;
        private Integer candidatesNeeded;
        private String source;
        private List<CandidateMatch> matches;
        private Integer matchesFound;
        private Boolean fullyFilled;
        
        // Constructors
        public PositionMatch() {}
        
        // Getters and Setters
        public Double getMinYoeRequired() {
            return minYoeRequired;
        }
        
        public void setMinYoeRequired(Double minYoeRequired) {
            this.minYoeRequired = minYoeRequired;
        }
        
        public Integer getCandidatesNeeded() {
            return candidatesNeeded;
        }
        
        public void setCandidatesNeeded(Integer candidatesNeeded) {
            this.candidatesNeeded = candidatesNeeded;
        }
        
        public String getSource() {
            return source;
        }
        
        public void setSource(String source) {
            this.source = source;
        }
        
        public List<CandidateMatch> getMatches() {
            return matches;
        }
        
        public void setMatches(List<CandidateMatch> matches) {
            this.matches = matches;
        }
        
        public Integer getMatchesFound() {
            return matchesFound;
        }
        
        public void setMatchesFound(Integer matchesFound) {
            this.matchesFound = matchesFound;
        }
        
        public Boolean getFullyFilled() {
            return fullyFilled;
        }
        
        public void setFullyFilled(Boolean fullyFilled) {
            this.fullyFilled = fullyFilled;
        }
    }
    
    public static class MatchingSummary {
        private Integer totalPositions;
        private Integer totalCandidatesNeeded;
        private Integer totalMatchesFound;
        private Integer fullyFilledPositions;
        private Double overallFillRate;
        
        // Constructors
        public MatchingSummary() {}
        
        // Getters and Setters
        public Integer getTotalPositions() {
            return totalPositions;
        }
        
        public void setTotalPositions(Integer totalPositions) {
            this.totalPositions = totalPositions;
        }
        
        public Integer getTotalCandidatesNeeded() {
            return totalCandidatesNeeded;
        }
        
        public void setTotalCandidatesNeeded(Integer totalCandidatesNeeded) {
            this.totalCandidatesNeeded = totalCandidatesNeeded;
        }
        
        public Integer getTotalMatchesFound() {
            return totalMatchesFound;
        }
        
        public void setTotalMatchesFound(Integer totalMatchesFound) {
            this.totalMatchesFound = totalMatchesFound;
        }
        
        public Integer getFullyFilledPositions() {
            return fullyFilledPositions;
        }
        
        public void setFullyFilledPositions(Integer fullyFilledPositions) {
            this.fullyFilledPositions = fullyFilledPositions;
        }
        
        public Double getOverallFillRate() {
            return overallFillRate;
        }
        
        public void setOverallFillRate(Double overallFillRate) {
            this.overallFillRate = overallFillRate;
        }
    }
}