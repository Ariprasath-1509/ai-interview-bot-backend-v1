package com.benchreadiness.auth.entity;

public enum UserRole {
    CANDIDATE(5),
    INTERVIEWER(20),
    HR(30),
    COMPLIANCE(40),
    BENCH_MANAGER(50),
    ADMIN(60);

    private final int rank;

    UserRole(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }

    public boolean isStaff() {
        return this != CANDIDATE;
    }
}
