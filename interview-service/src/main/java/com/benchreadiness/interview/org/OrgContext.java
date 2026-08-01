package com.benchreadiness.interview.org;

/**
 * Per-request holder for the caller's organization (from the gateway-forwarded
 * {@code X-User-Org} header, itself sourced from the JWT's {@code org} claim).
 * Populated by {@link com.benchreadiness.interview.config.HeaderAuthenticationFilter}
 * and must be cleared at the end of every request to avoid leaking across pooled threads.
 * Mirrors {@link com.benchreadiness.interview.branch.BranchContext} exactly — org (tenant)
 * and branch (dev/testing environment) are independent scoping dimensions.
 */
public final class OrgContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private OrgContext() {}

    public static void set(String orgCode) {
        if (orgCode != null && !orgCode.isBlank()) {
            CURRENT.set(orgCode.trim().toUpperCase());
        }
    }

    public static String get() {
        return CURRENT.get();
    }

    /** Falls back to the default tenant when no org context is present (e.g. pre-rollout tokens). */
    public static String getOrDefault() {
        String org = CURRENT.get();
        return org != null ? org : "TESTYANTRA";
    }

    public static void clear() {
        CURRENT.remove();
    }
}
