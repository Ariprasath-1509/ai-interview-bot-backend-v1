package com.benchreadiness.ops.branch;

import java.util.Set;

public final class BranchAccess {

    private static final Set<String> TESTING_ROLES = Set.of("TESTING_ADMIN", "TESTING_RECRUITER");
    private static final Set<String> DEVELOPMENT_ROLES = Set.of("ADMIN", "RECRUITER");

    private BranchAccess() {}

    public static String resolveAllowedBranch(String role) {
        if (role == null) {
            return "DEVELOPMENT";
        }
        if ("SUPER_ADMIN".equals(role)) {
            return null;
        }
        if (TESTING_ROLES.contains(role)) {
            return "TESTING";
        }
        if (DEVELOPMENT_ROLES.contains(role)) {
            return "DEVELOPMENT";
        }
        return "DEVELOPMENT";
    }

    public static boolean canAccessBranch(String role, String entityBranch) {
        String allowed = resolveAllowedBranch(role);
        if (allowed == null) {
            return true;
        }
        String normalized = entityBranch == null || entityBranch.isBlank() ? "DEVELOPMENT" : entityBranch.trim().toUpperCase();
        return allowed.equals(normalized);
    }
}
