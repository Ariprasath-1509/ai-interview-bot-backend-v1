package com.benchreadiness.ops.org;

/** Per-request holder for the caller's org (from X-User-Org). Cleared at the end of every request. */
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
