package com.benchreadiness.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** SUPER_ADMIN creates a new organization (tenant) and its first org-admin user in one call. */
public class CreateOrganizationRequest {

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    @NotNull
    private String type; // DEMO | LIVE

    /** Only meaningful for DEMO orgs — null means unlimited. */
    private Integer maxInterviews;
    private Integer maxCandidates;
    private Integer maxClients;

    @NotBlank
    private String adminName;

    @NotBlank @Email
    private String adminEmail;

    @NotBlank @Size(min = 6, message = "Password must be at least 6 characters")
    private String adminPassword;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Integer getMaxInterviews() { return maxInterviews; }
    public void setMaxInterviews(Integer maxInterviews) { this.maxInterviews = maxInterviews; }
    public Integer getMaxCandidates() { return maxCandidates; }
    public void setMaxCandidates(Integer maxCandidates) { this.maxCandidates = maxCandidates; }
    public Integer getMaxClients() { return maxClients; }
    public void setMaxClients(Integer maxClients) { this.maxClients = maxClients; }
    public String getAdminName() { return adminName; }
    public void setAdminName(String adminName) { this.adminName = adminName; }
    public String getAdminEmail() { return adminEmail; }
    public void setAdminEmail(String adminEmail) { this.adminEmail = adminEmail; }
    public String getAdminPassword() { return adminPassword; }
    public void setAdminPassword(String adminPassword) { this.adminPassword = adminPassword; }
}
