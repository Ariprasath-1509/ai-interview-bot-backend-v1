package com.benchreadiness.screening.dto;

import jakarta.validation.constraints.NotBlank;

public class VerifyEmailRequest {

    @NotBlank
    private String email;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
