package com.benchreadiness.review.org;

/**
 * Per-request holder for the caller's organization (from the gateway-forwarded X-User-Org header,
 * itself sourced from the JWT's org claim). Populated by HeaderAuthenticationFilter and must be
 * cleared at the end of every request to avoid leaking across pooled threads.
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

    public static void clear() {
        CURRENT.remove();
    }
}
