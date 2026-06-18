package com.benchreadiness.auth.branch;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BranchAccessTest {

    @BeforeEach
    void enableSegregation() {
        BranchSegregation.setEnabled(true);
    }

    @AfterEach
    void resetSegregation() {
        BranchSegregation.setEnabled(false);
    }

    @Test
    void whenSegregationFlagDisabled_branchFiltersStillApplyForScopedRoles() {
        BranchSegregation.setEnabled(false);
        assertEquals("DEVELOPMENT", BranchAccess.resolveAllowedBranch("ADMIN"));
        assertFalse(BranchAccess.canAccessBranch("TESTING_ADMIN", "DEVELOPMENT"));
        assertEquals("DEVELOPMENT", BranchAccess.resolveBranchForCreate("ADMIN", "TESTING"));
    }

    @Test
    void resolveAllowedBranch_superAdminSeesAll() {
        assertNull(BranchAccess.resolveAllowedBranch("SUPER_ADMIN"));
    }

    @Test
    void resolveAllowedBranch_testingRolesSeeTestingOnly() {
        assertEquals("TESTING", BranchAccess.resolveAllowedBranch("TESTING_ADMIN"));
        assertEquals("TESTING", BranchAccess.resolveAllowedBranch("TESTING_RECRUITER"));
    }

    @Test
    void resolveAllowedBranch_developmentRolesSeeDevelopmentOnly() {
        assertEquals("DEVELOPMENT", BranchAccess.resolveAllowedBranch("ADMIN"));
        assertEquals("DEVELOPMENT", BranchAccess.resolveAllowedBranch("RECRUITER"));
    }

    @Test
    void canAccessBranch_enforcesRoleScope() {
        assertTrue(BranchAccess.canAccessBranch("TESTING_ADMIN", "TESTING"));
        assertFalse(BranchAccess.canAccessBranch("TESTING_ADMIN", "DEVELOPMENT"));
        assertTrue(BranchAccess.canAccessBranch("ADMIN", "DEVELOPMENT"));
        assertFalse(BranchAccess.canAccessBranch("RECRUITER", "TESTING"));
        assertTrue(BranchAccess.canAccessBranch("SUPER_ADMIN", "TESTING"));
        assertTrue(BranchAccess.canAccessBranch("SUPER_ADMIN", "DEVELOPMENT"));
    }

    @Test
    void resolveBranchForCreate_respectsRoleWhenRestricted() {
        assertEquals("TESTING", BranchAccess.resolveBranchForCreate("TESTING_ADMIN", "DEVELOPMENT"));
        assertEquals("DEVELOPMENT", BranchAccess.resolveBranchForCreate("ADMIN", "TESTING"));
    }

    @Test
    void resolveBranchForCreate_superAdminUsesRequestedBranch() {
        assertEquals("TESTING", BranchAccess.resolveBranchForCreate("SUPER_ADMIN", "TESTING"));
        assertEquals("DEVELOPMENT", BranchAccess.resolveBranchForCreate("SUPER_ADMIN", null));
    }

    @Test
    void resolveBranchForCreate_superAdminCanChooseBranch() {
        assertEquals("TESTING", BranchAccess.resolveBranchForCreate("SUPER_ADMIN", "TESTING"));
        assertEquals("DEVELOPMENT", BranchAccess.resolveBranchForCreate("SUPER_ADMIN", null));
    }

    @Test
    void resolveStaffBranch_whenSegregationDisabled_stillMapsByRole() {
        BranchSegregation.setEnabled(false);
        assertEquals("TESTING", BranchAccess.resolveStaffBranch("TESTING_ADMIN"));
        assertEquals("TESTING", BranchAccess.resolveStaffBranch("TESTING_RECRUITER"));
        assertEquals("DEVELOPMENT", BranchAccess.resolveStaffBranch("ADMIN"));
        assertEquals("DEVELOPMENT", BranchAccess.resolveStaffBranch("RECRUITER"));
    }

    @Test
    void resolveStaffBranch_mapsRoleToStoredBranch() {
        assertEquals("TESTING", BranchAccess.resolveStaffBranch("TESTING_ADMIN"));
        assertEquals("TESTING", BranchAccess.resolveStaffBranch("TESTING_RECRUITER"));
        assertEquals("DEVELOPMENT", BranchAccess.resolveStaffBranch("ADMIN"));
        assertEquals("DEVELOPMENT", BranchAccess.resolveStaffBranch("RECRUITER"));
        assertEquals("DEVELOPMENT", BranchAccess.resolveStaffBranch("SUPER_ADMIN"));
    }
}
