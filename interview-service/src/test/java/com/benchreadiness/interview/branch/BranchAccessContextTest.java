package com.benchreadiness.interview.branch;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Covers the per-user branch override: resolveAllowedBranch must prefer the
 * gateway-forwarded X-User-Branch (via BranchContext) over the fixed role default,
 * so a staff member assigned to a branch beyond DEVELOPMENT/TESTING is actually
 * scoped to it instead of always falling back to their role's conventional branch.
 */
class BranchAccessContextTest {

    @AfterEach
    void clearContext() {
        BranchContext.clear();
        BranchRegistry.refresh(List.of("DEVELOPMENT", "TESTING"));
    }

    @Test
    void superAdminAlwaysSeesAllBranchesRegardlessOfContext() {
        BranchContext.set("STAGING");
        BranchRegistry.refresh(List.of("DEVELOPMENT", "TESTING", "STAGING"));
        assertNull(BranchAccess.resolveAllowedBranch("SUPER_ADMIN"));
    }

    @Test
    void contextBranchOverridesRoleDefaultWhenRegisteredAndValid() {
        BranchRegistry.refresh(List.of("DEVELOPMENT", "TESTING", "STAGING"));
        BranchContext.set("STAGING");
        // RECRUITER's legacy default is DEVELOPMENT, but this recruiter is assigned to STAGING.
        assertEquals("STAGING", BranchAccess.resolveAllowedBranch("RECRUITER"));
    }

    @Test
    void unregisteredContextBranchFallsBackToRoleDefault() {
        // Registry hasn't been synced with "STAGING" yet — don't trust an unvalidated header.
        BranchContext.set("STAGING");
        assertEquals("DEVELOPMENT", BranchAccess.resolveAllowedBranch("RECRUITER"));
    }

    @Test
    void missingContextFallsBackToLegacyRoleMapping() {
        assertEquals("TESTING", BranchAccess.resolveAllowedBranch("TESTING_ADMIN"));
        assertEquals("DEVELOPMENT", BranchAccess.resolveAllowedBranch("ADMIN"));
        assertEquals("DEVELOPMENT", BranchAccess.resolveAllowedBranch(null));
    }

    @Test
    void canAccessBranchHonorsContextOverride() {
        BranchRegistry.refresh(List.of("DEVELOPMENT", "TESTING", "STAGING"));
        BranchContext.set("STAGING");
        assertEquals(true, BranchAccess.canAccessBranch("RECRUITER", "STAGING"));
        assertEquals(false, BranchAccess.canAccessBranch("RECRUITER", "DEVELOPMENT"));
    }
}
