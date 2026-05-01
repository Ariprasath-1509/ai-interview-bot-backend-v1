package com.benchreadiness.observer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ClientCreatedRequest {
    @NotBlank
    private String clientId;
    
    @NotBlank
    private String clientName;
    
    @NotBlank
    private String jdRole;
    
    @NotNull
    private Integer benchB2bCandidatesNeeded;
    
    @NotNull
    private Integer marketCandidatesNeeded;

    public ClientCreatedRequest() {}

    public ClientCreatedRequest(String clientId, String clientName, String jdRole, Integer benchB2bCandidatesNeeded, Integer marketCandidatesNeeded) {
        this.clientId = clientId;
        this.clientName = clientName;
        this.jdRole = jdRole;
        this.benchB2bCandidatesNeeded = benchB2bCandidatesNeeded;
        this.marketCandidatesNeeded = marketCandidatesNeeded;
    }

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

    public String getJdRole() {
        return jdRole;
    }

    public void setJdRole(String jdRole) {
        this.jdRole = jdRole;
    }

    public Integer getBenchB2bCandidatesNeeded() {
        return benchB2bCandidatesNeeded;
    }

    public void setBenchB2bCandidatesNeeded(Integer benchB2bCandidatesNeeded) {
        this.benchB2bCandidatesNeeded = benchB2bCandidatesNeeded;
    }

    public Integer getMarketCandidatesNeeded() {
        return marketCandidatesNeeded;
    }

    public void setMarketCandidatesNeeded(Integer marketCandidatesNeeded) {
        this.marketCandidatesNeeded = marketCandidatesNeeded;
    }
}