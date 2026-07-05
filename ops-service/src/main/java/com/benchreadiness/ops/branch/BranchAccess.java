package com.benchreadiness.ops.branch;

import java.util.Set;

public final class BranchAccess {

    private static final Set<String> TESTING_ROLES = Set.of("TESTING_ADMIN", "TESTING_RECRUITER");
    private static final Set<String> DEVELOPMENT_ROLES = Set.of("ADMIN", "RECRUITER");

    private BranchAccess() {}

    public static String resolveAllowedBranch(String role) {
        if ("SUPER_ADMIN".equals(role)) {
            return null;
        }
        // The gateway forwards X-User-Branch from the JWT's branch claim, which now reflects
        // the user's actual persisted assignment rather than a fixed role default. Falls back
        // to the legacy role-based default when absent (older tokens, internal calls).
        String contextBranch = BranchContext.get();
        if (contextBranch != null && BranchRegistry.isValid(contextBranch)) {
            return contextBranch;
        }
        if (role == null) {
            return "DEVELOPMENT";
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
