package com.benchreadiness.ops.observer.dto;

import jakarta.validation.constraints.NotBlank;

public class ClientCreatedRequest {
    @NotBlank private String clientId;
    @NotBlank private String clientName;
    private String jdRole;
    private Integer benchB2bCandidatesNeeded = 0;
    private Integer marketCandidatesNeeded = 0;

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }
    public String getJdRole() { return jdRole; }
    public void setJdRole(String jdRole) { this.jdRole = jdRole; }
    public Integer getBenchB2bCandidatesNeeded() { return benchB2bCandidatesNeeded; }
    public void setBenchB2bCandidatesNeeded(Integer benchB2bCandidatesNeeded) { this.benchB2bCandidatesNeeded = benchB2bCandidatesNeeded; }
    public Integer getMarketCandidatesNeeded() { return marketCandidatesNeeded; }
    public void setMarketCandidatesNeeded(Integer marketCandidatesNeeded) { this.marketCandidatesNeeded = marketCandidatesNeeded; }
}
