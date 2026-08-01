package com.benchreadiness.interview.org;

/**
 * Resolves which organization a staff member may access. Unlike {@link com.benchreadiness.interview.branch.BranchAccess},
 * org isn't inferred from role — it's the caller's actual tenant, read from {@link OrgContext}. Only
 * SUPER_ADMIN is cross-org.
 */
public final class OrgAccess {

    private OrgAccess() {}

    /** @return the caller's org code, or {@code null} when the role may access every org (super-admin) */
    public static String resolveAllowedOrg(String role) {
        if ("SUPER_ADMIN".equals(role)) {
            return null;
        }
        return OrgContext.getOrDefault();
    }

    public static boolean canAccessOrg(String role, String entityOrgCode) {
        String allowed = resolveAllowedOrg(role);
        if (allowed == null) {
            return true;
        }
        return allowed.equalsIgnoreCase(entityOrgCode);
    }

    public static String defaultOrg() {
        return "TESTYANTRA";
    }

    /** Callers always create records in their own org — there's no "requested org" override like branch has. */
    public static String resolveOrgForCreate(String role) {
        String allowed = resolveAllowedOrg(role);
        return allowed != null ? allowed : defaultOrg();
    }
}
