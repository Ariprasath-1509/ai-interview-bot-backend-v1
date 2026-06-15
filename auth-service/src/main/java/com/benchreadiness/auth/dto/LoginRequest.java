package com.benchreadiness.auth.dto;

import com.benchreadiness.auth.entity.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Login credentials")
public class LoginRequest {
    @NotBlank
    @Schema(description = "Email address", example = "user@example.com")
    private String username;
    @NotBlank
    @Schema(description = "Account password")
    private String password;
    @Schema(description = "Optional role guard for staff portals")
    private UserRole role = null;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
}
