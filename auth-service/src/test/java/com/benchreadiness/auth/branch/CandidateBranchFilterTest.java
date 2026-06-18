package com.benchreadiness.auth.branch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CandidateBranchFilterTest {

    @Test
    void recruiterRole_isScopedToDevelopmentBranch() {
        assertEquals("DEVELOPMENT", BranchAccess.resolveAllowedBranch("RECRUITER"));
        assertTrue(BranchAccess.canAccessBranch("RECRUITER", "DEVELOPMENT"));
        assertFalse(BranchAccess.canAccessBranch("RECRUITER", "TESTING"));
    }

    @Test
    void testingRecruiterRole_isScopedToTestingBranch() {
        assertEquals("TESTING", BranchAccess.resolveAllowedBranch("TESTING_RECRUITER"));
        assertTrue(BranchAccess.canAccessBranch("TESTING_RECRUITER", "TESTING"));
        assertFalse(BranchAccess.canAccessBranch("TESTING_RECRUITER", "DEVELOPMENT"));
    }

    @Test
    void testingAdminRole_isScopedToTestingBranch() {
        assertEquals("TESTING", BranchAccess.resolveAllowedBranch("TESTING_ADMIN"));
    }
}
