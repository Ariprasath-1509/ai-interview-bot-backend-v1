package com.benchreadiness.auth.dto;

/** SUPER_ADMIN adjusts an existing org's type, status, or demo limits. All fields optional/partial. */
public class UpdateOrganizationRequest {

    private String type;   // DEMO | LIVE
    private String status; // ACTIVE | SUSPENDED
    private Integer maxInterviews;
    private Integer maxCandidates;
    private Integer maxClients;
    private boolean clearMaxInterviews;
    private boolean clearMaxCandidates;
    private boolean clearMaxClients;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getMaxInterviews() { return maxInterviews; }
    public void setMaxInterviews(Integer maxInterviews) { this.maxInterviews = maxInterviews; }
    public Integer getMaxCandidates() { return maxCandidates; }
    public void setMaxCandidates(Integer maxCandidates) { this.maxCandidates = maxCandidates; }
    public Integer getMaxClients() { return maxClients; }
    public void setMaxClients(Integer maxClients) { this.maxClients = maxClients; }
    public boolean isClearMaxInterviews() { return clearMaxInterviews; }
    public void setClearMaxInterviews(boolean clearMaxInterviews) { this.clearMaxInterviews = clearMaxInterviews; }
    public boolean isClearMaxCandidates() { return clearMaxCandidates; }
    public void setClearMaxCandidates(boolean clearMaxCandidates) { this.clearMaxCandidates = clearMaxCandidates; }
    public boolean isClearMaxClients() { return clearMaxClients; }
    public void setClearMaxClients(boolean clearMaxClients) { this.clearMaxClients = clearMaxClients; }
}
