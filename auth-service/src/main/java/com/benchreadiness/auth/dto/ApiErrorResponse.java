package com.benchreadiness.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Error response")
public class ApiErrorResponse {

    @Schema(example = "false")
    private boolean ok = false;

    @Schema(example = "Invalid credentials")
    private String error;

    public ApiErrorResponse() {}

    public ApiErrorResponse(String error) {
        this.error = error;
    }

    public boolean isOk() { return ok; }
    public void setOk(boolean ok) { this.ok = ok; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
