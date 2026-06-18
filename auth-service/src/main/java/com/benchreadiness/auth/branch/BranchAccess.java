package com.benchreadiness.auth.branch;

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
     * @return allowed branch code, or {@code null} when the role may access every branch (super-admin)
     */
    public static String resolveAllowedBranch(String role) {
        if (role == null) {
            return Branch.DEVELOPMENT.code();
        }
        if ("SUPER_ADMIN".equals(role)) {
            return null;
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

    /** Branch stored on staff accounts; always derived from role (independent of segregation flag). */
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

    public static String resolveStaffBranch(com.benchreadiness.auth.entity.UserRole role) {
        return role != null ? resolveStaffBranch(role.name()) : defaultBranch();
    }
}
