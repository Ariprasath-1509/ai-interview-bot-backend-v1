package com.benchreadiness.auth.feature;

/**
 * Toggleable product features. Each org can be individually opted in/out of these by SUPER_ADMIN;
 * a feature with no explicit row in {@code org_features} is enabled by default (see
 * {@link OrganizationFeatureService}). Platform-admin-only capabilities (organizations, staff,
 * settings) are intentionally excluded — those are role-gated, not tenant-gated.
 */
public enum FeatureKey {
    INTERVIEWS,
    REVIEW,
    SCREENING,
    CANDIDATES,
    BULK_IMPORT,
    DEPLOYMENT_IMPORT,
    CLIENTS,
    RECRUITER_BOT,
    CALENDAR,
    COMPLIANCE,
    MASTER_DATA,
    QUESTION_BANK,
    ANALYTICS
}
