package com.benchreadiness.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Success response with a message")
public class ApiMessageResponse {

    @Schema(example = "true")
    private boolean ok = true;

    @Schema(example = "Registration successful. You can now log in.")
    private String message;

    public ApiMessageResponse() {}

    public ApiMessageResponse(boolean ok, String message) {
        this.ok = ok;
        this.message = message;
    }

    public boolean isOk() { return ok; }
    public void setOk(boolean ok) { this.ok = ok; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
