package com.benchreadiness.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Logout request")
public class LogoutRequest {

    @Schema(description = "Refresh token to revoke")
    private String refreshToken;

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}
