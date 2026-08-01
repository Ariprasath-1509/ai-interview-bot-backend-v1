package com.benchreadiness.auth.org;

/** DEMO orgs get capped, feature-limited access (trial); LIVE orgs are unrestricted. */
public enum OrganizationType {
    DEMO,
    LIVE;

    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) return false;
        for (OrganizationType t : values()) {
            if (t.name().equals(value.trim().toUpperCase())) return true;
        }
        return false;
    }
}
