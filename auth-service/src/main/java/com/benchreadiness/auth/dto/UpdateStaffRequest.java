package com.benchreadiness.auth.dto;

import com.benchreadiness.auth.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class UpdateStaffRequest {

    @NotBlank
    private String name;

    @NotBlank @Email
    private String email;

    private String password;

    @NotNull
    private UserRole role;

    private String adminSource;

    /** Optional: explicit branch code (validated against master data). Null leaves the stored branch unchanged. */
    private String branch;

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
}
