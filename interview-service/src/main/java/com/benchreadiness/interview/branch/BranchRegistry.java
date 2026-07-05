package com.benchreadiness.interview.branch;

import java.util.Collection;
import java.util.Set;

/**
 * Live set of valid branch codes, kept in sync with auth-service's BRANCH master-data
 * category by {@link BranchRegistrySync}. Starts with the two legacy codes so validation
 * behaves exactly as before until the first successful sync.
 */
public final class BranchRegistry {

    private static volatile Set<String> activeCodes = Set.of("DEVELOPMENT", "TESTING");

    private BranchRegistry() {}

    public static void refresh(Collection<String> codes) {
        if (codes != null && !codes.isEmpty()) {
            activeCodes = Set.copyOf(codes);
        }
    }

    public static boolean isValid(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return activeCodes.contains(code.trim().toUpperCase());
    }
}
