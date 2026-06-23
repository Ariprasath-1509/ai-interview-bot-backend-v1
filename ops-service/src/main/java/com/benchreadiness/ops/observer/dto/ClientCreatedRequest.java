package com.benchreadiness.ops.observer.dto;

import jakarta.validation.constraints.NotBlank;

public class ClientCreatedRequest {
    @NotBlank private String clientId;
    @NotBlank private String clientName;
    private String jdRole;
    private String jdDescription;
    private Integer positionsVacant = 0;
    private Integer benchB2bCandidatesNeeded = 0;
    private Integer marketCandidatesNeeded = 0;
    private String skillRequirementsSummary;
    private String jdFileName;

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }
    public String getJdRole() { return jdRole; }
    public void setJdRole(String jdRole) { this.jdRole = jdRole; }
    public String getJdDescription() { return jdDescription; }
    public void setJdDescription(String jdDescription) { this.jdDescription = jdDescription; }
    public Integer getPositionsVacant() { return positionsVacant; }
    public void setPositionsVacant(Integer positionsVacant) { this.positionsVacant = positionsVacant; }
    public Integer getBenchB2bCandidatesNeeded() { return benchB2bCandidatesNeeded; }
    public void setBenchB2bCandidatesNeeded(Integer benchB2bCandidatesNeeded) { this.benchB2bCandidatesNeeded = benchB2bCandidatesNeeded; }
    public Integer getMarketCandidatesNeeded() { return marketCandidatesNeeded; }
    public void setMarketCandidatesNeeded(Integer marketCandidatesNeeded) { this.marketCandidatesNeeded = marketCandidatesNeeded; }
    public String getSkillRequirementsSummary() { return skillRequirementsSummary; }
    public void setSkillRequirementsSummary(String skillRequirementsSummary) { this.skillRequirementsSummary = skillRequirementsSummary; }
    public String getJdFileName() { return jdFileName; }
    public void setJdFileName(String jdFileName) { this.jdFileName = jdFileName; }
}
