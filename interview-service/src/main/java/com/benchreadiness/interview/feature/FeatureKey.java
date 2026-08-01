package com.benchreadiness.interview.feature;

/**
 * Mirrors com.benchreadiness.auth.feature.FeatureKey in auth-service — the source of truth for
 * per-org entitlements. Kept as a plain enum copy (no shared library between services); only
 * INTERVIEWS, CLIENTS, and RECRUITER_BOT are currently enforced in this service.
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
