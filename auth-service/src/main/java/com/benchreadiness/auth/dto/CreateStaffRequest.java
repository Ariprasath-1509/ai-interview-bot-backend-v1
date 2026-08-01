package com.benchreadiness.auth.dto;

import com.benchreadiness.auth.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateStaffRequest {

    @NotBlank
    private String name;

    @NotBlank @Email
    private String email;

    @NotBlank @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotNull
    private UserRole role;

    private String adminSource;

    /** Optional: explicit branch code (validated against master data). Defaults from role when absent. */
    private String branch;

    /** Optional: which organization (tenant) this staff account belongs to. Defaults to TESTYANTRA when absent. */
    private String orgCode;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
    public String getAdminSource() { return adminSource; }
    public void setAdminSource(String adminSource) { this.adminSource = adminSource; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public String getOrgCode() { return orgCode; }
    public void setOrgCode(String orgCode) { this.orgCode = orgCode; }
}
