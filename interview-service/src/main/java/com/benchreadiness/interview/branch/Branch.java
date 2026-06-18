package com.benchreadiness.interview.branch;

public enum Branch {
    DEVELOPMENT("DEVELOPMENT"),
    TESTING("TESTING");

    private final String code;

    Branch(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static String normalize(String branch) {
        if (branch == null || branch.isBlank()) {
            return DEVELOPMENT.code;
        }
        return branch.trim().toUpperCase();
    }

    public static boolean isValid(String branch) {
        if (branch == null || branch.isBlank()) {
            return false;
        }
        String normalized = branch.trim().toUpperCase();
        for (Branch value : values()) {
            if (value.code.equals(normalized)) {
                return true;
            }
        }
        return false;
    }
}
