package com.benchreadiness.screening.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public class UpdateDeadlineRequest {

    @NotNull
    private Instant deadline;

    public Instant getDeadline() { return deadline; }
    public void setDeadline(Instant deadline) { this.deadline = deadline; }
}
