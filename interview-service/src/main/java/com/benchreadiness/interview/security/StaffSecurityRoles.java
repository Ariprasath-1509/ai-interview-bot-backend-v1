package com.benchreadiness.interview.security;

public final class StaffSecurityRoles {

    public static final String READ =
        "ADMIN', 'TESTING_ADMIN', 'SUPER_ADMIN', 'RECRUITER', 'TESTING_RECRUITER";

    public static final String ADMIN =
        "ADMIN', 'TESTING_ADMIN', 'SUPER_ADMIN";

    public static final String READ_AND_CANDIDATE =
        "ADMIN', 'TESTING_ADMIN', 'SUPER_ADMIN', 'RECRUITER', 'TESTING_RECRUITER', 'CANDIDATE";

    private StaffSecurityRoles() {}
}
