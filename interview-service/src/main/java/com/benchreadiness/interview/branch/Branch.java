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

    /** Delegates to {@link BranchRegistry}, which is kept in sync with the BRANCH master-data
     * category so branches added after DEVELOPMENT/TESTING validate correctly. */
    public static boolean isValid(String branch) {
        return BranchRegistry.isValid(branch);
    }
}
