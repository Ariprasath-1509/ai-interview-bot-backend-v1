package com.benchreadiness.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Simple success response")
public class ApiSuccessResponse {

    @Schema(example = "true")
    private boolean ok = true;

    public ApiSuccessResponse() {}

    public ApiSuccessResponse(boolean ok) {
        this.ok = ok;
    }

    public boolean isOk() { return ok; }
    public void setOk(boolean ok) { this.ok = ok; }
}
