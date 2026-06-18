package com.benchreadiness.auth.branch;

/** Runtime toggle for branch segregation (see {@code app.branch-segregation.enabled}). */
public final class BranchSegregation {

    private static volatile boolean enabled;

    private BranchSegregation() {}

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }
}
