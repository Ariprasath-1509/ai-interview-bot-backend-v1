package com.benchreadiness.interview.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ClientMatchingResult {
    private String clientId;
    private String clientName;
    private String jdRole;
    private String jdDescription;
    private String source; // BENCH_B2B or MARKET
    private List<CandidateMatch> matches;
    private Map<String, Object> summary;
    private LocalDateTime computedAt;
    private String cacheSource; // "cached" or "ai-fresh"

    // Constructors
    public ClientMatchingResult() {}

    public ClientMatchingResult(String clientId, String clientName, String jdRole, 
                               String jdDescription, String source, List<CandidateMatch> matches,
                               Map<String, Object> summary, LocalDateTime computedAt, String cacheSource) {
        this.clientId = clientId;
        this.clientName = clientName;
        this.jdRole = jdRole;
        this.jdDescription = jdDescription;
        this.source = source;
        this.matches = matches;
        this.summary = summary;
        this.computedAt = computedAt;
        this.cacheSource = cacheSource;
    }

    // Getters and Setters
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }

    public String getJdRole() { return jdRole; }
    public void setJdRole(String jdRole) { this.jdRole = jdRole; }

    public String getJdDescription() { return jdDescription; }
    public void setJdDescription(String jdDescription) { this.jdDescription = jdDescription; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public List<CandidateMatch> getMatches() { return matches; }
    public void setMatches(List<CandidateMatch> matches) { this.matches = matches; }

    public Map<String, Object> getSummary() { return summary; }
    public void setSummary(Map<String, Object> summary) { this.summary = summary; }

    public LocalDateTime getComputedAt() { return computedAt; }
    public void setComputedAt(LocalDateTime computedAt) { this.computedAt = computedAt; }

    public String getCacheSource() { return cacheSource; }
    public void setCacheSource(String cacheSource) { this.cacheSource = cacheSource; }
}
