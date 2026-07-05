package com.benchreadiness.interview.branch;

/**
 * Per-request holder for the caller's assigned branch (from the gateway-forwarded
 * {@code X-User-Branch} header, itself sourced from the JWT's {@code branch} claim).
 * Populated by {@link com.benchreadiness.interview.config.HeaderAuthenticationFilter}
 * and must be cleared at the end of every request to avoid leaking across pooled threads.
 */
public final class BranchContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private BranchContext() {}

    public static void set(String branch) {
        if (branch != null && !branch.isBlank()) {
            CURRENT.set(branch.trim().toUpperCase());
        }
    }

    public static String get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
