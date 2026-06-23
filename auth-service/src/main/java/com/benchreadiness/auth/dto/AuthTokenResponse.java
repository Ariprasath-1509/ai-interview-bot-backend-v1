package com.benchreadiness.auth.dto;

import com.benchreadiness.auth.entity.User;
import com.benchreadiness.auth.service.JwtService;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Authentication token response returned by login and refresh")
public class AuthTokenResponse {

    @Schema(example = "true")
    private boolean ok = true;

    @Schema(description = "JWT access token")
    private String token;

    @Schema(description = "JWT refresh token")
    private String refreshToken;

    @Schema(description = "Access token lifetime in seconds", example = "1800")
    private long expiresIn;

    @Schema(description = "User role", example = "CANDIDATE")
    private String role;

    @Schema(description = "Display name", example = "Jane Doe")
    private String name;

    @Schema(description = "Admin source scope (ADMIN role only)", example = "B2B")
    private String adminSource;

    @Schema(description = "Branch scope for staff (TESTING or DEVELOPMENT)", example = "DEVELOPMENT")
    private String branch;

    public static AuthTokenResponse from(User user, JwtService.TokenPair pair) {
        AuthTokenResponse response = from(pair);
        response.setRole(user.getRole().name());
        response.setName(user.getName() != null ? user.getName() : "");
        if (user.getAdminSource() != null) {
            response.setAdminSource(user.getAdminSource());
        }
        if (user.getRole().isStaff()) {
            response.setBranch(com.benchreadiness.auth.branch.BranchAccess.resolveStaffBranch(user.getRole()));
        } else if (user.getBranch() != null) {
            response.setBranch(user.getBranch());
        }
        return response;
    }

    public static AuthTokenResponse from(JwtService.TokenPair pair) {
        AuthTokenResponse response = new AuthTokenResponse();
        response.setToken(pair.accessToken());
        response.setRefreshToken(pair.refreshToken());
        response.setExpiresIn(pair.expiresInSeconds());
        return response;
    }

    public boolean isOk() { return ok; }
    public void setOk(boolean ok) { this.ok = ok; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public long getExpiresIn() { return expiresIn; }
    public void setExpiresIn(long expiresIn) { this.expiresIn = expiresIn; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAdminSource() { return adminSource; }
    public void setAdminSource(String adminSource) { this.adminSource = adminSource; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
}
