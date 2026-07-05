package com.benchreadiness.interview.branch;

import java.util.Set;

/**
 * Resolves which branch a staff role may access.
 * {@code null} from {@link #resolveAllowedBranch(String)} means all branches (super-admin).
 */
public final class BranchAccess {

    private static final Set<String> TESTING_ROLES = Set.of("TESTING_ADMIN", "TESTING_RECRUITER");
    private static final Set<String> DEVELOPMENT_ROLES = Set.of("ADMIN", "RECRUITER");

    private BranchAccess() {}

    /**
     * @return allowed branch code, or {@code null} when the role may access every branch
     */
    public static String resolveAllowedBranch(String role) {
        if ("SUPER_ADMIN".equals(role)) {
            return null;
        }
        // The gateway forwards X-User-Branch from the JWT's branch claim, which now reflects
        // the user's actual persisted assignment rather than a fixed role default — this lets
        // a branch added after the two originals actually gate access for a staff member
        // assigned to it. Falls back to the legacy role-based default when absent (older
        // tokens, internal calls) so behavior is unchanged for anything not yet re-authenticated.
        String contextBranch = BranchContext.get();
        if (contextBranch != null && BranchRegistry.isValid(contextBranch)) {
            return contextBranch;
        }
        if (role == null) {
            return Branch.DEVELOPMENT.code();
        }
        if (TESTING_ROLES.contains(role)) {
            return Branch.TESTING.code();
        }
        if (DEVELOPMENT_ROLES.contains(role)) {
            return Branch.DEVELOPMENT.code();
        }
        return Branch.DEVELOPMENT.code();
    }

    public static boolean canAccessBranch(String role, String entityBranch) {
        String allowed = resolveAllowedBranch(role);
        if (allowed == null) {
            return true;
        }
        return allowed.equals(Branch.normalize(entityBranch));
    }

    public static String defaultBranch() {
        return Branch.DEVELOPMENT.code();
    }

    public static String resolveBranchForCreate(String role, String requestedBranch) {
        String allowed = resolveAllowedBranch(role);
        if (allowed != null) {
            return allowed;
        }
        if (requestedBranch != null && Branch.isValid(requestedBranch)) {
            return Branch.normalize(requestedBranch);
        }
        return defaultBranch();
    }

    public static boolean isTestingRole(String role) {
        return role != null && TESTING_ROLES.contains(role);
    }

    public static boolean isDevelopmentRole(String role) {
        return role != null && DEVELOPMENT_ROLES.contains(role);
    }

    public static String resolveStaffBranch(String role) {
        if (role == null) {
            return defaultBranch();
        }
        if (TESTING_ROLES.contains(role)) {
            return Branch.TESTING.code();
        }
        if (DEVELOPMENT_ROLES.contains(role)) {
            return Branch.DEVELOPMENT.code();
        }
        return defaultBranch();
    }
}
