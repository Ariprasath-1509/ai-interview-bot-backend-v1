package com.benchreadiness.ai.dto;

import java.util.List;
import java.util.Map;

public class MatchingRequest {
    private String clientId;
    private String jdTitle;
    private String jdDescription;
    private String clientName;
    private String source; // BENCH_B2B or MARKET
    private List<Map<String, Object>> candidates;
    private int maxCandidates = 10;

    // Getters and setters
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getJdTitle() { return jdTitle; }
    public void setJdTitle(String jdTitle) { this.jdTitle = jdTitle; }

    public String getJdDescription() { return jdDescription; }
    public void setJdDescription(String jdDescription) { this.jdDescription = jdDescription; }

    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public List<Map<String, Object>> getCandidates() { return candidates; }
    public void setCandidates(List<Map<String, Object>> candidates) { this.candidates = candidates; }

    public int getMaxCandidates() { return maxCandidates; }
    public void setMaxCandidates(int maxCandidates) { this.maxCandidates = maxCandidates; }
}