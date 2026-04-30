package com.benchreadiness.auth.entity;

public enum UserRole {
    CANDIDATE(5),
    RECRUITER(20),
    ADMIN(50),
    SUPER_ADMIN(60);

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
