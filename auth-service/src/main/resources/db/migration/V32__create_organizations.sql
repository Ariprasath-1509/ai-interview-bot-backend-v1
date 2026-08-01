-- Organization (tenant) separation: independent of branch (dev/testing) environment segregation.
-- org_code is a plain denormalized string column across every service's tables (same convention as
-- `branch`), not a cross-service FK, since each service owns its own schema/database.

CREATE TABLE IF NOT EXISTS auth_svc.organizations (
    id             VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    code           VARCHAR(64) NOT NULL UNIQUE,
    name           VARCHAR(255) NOT NULL,
    type           VARCHAR(16) NOT NULL DEFAULT 'LIVE',
    status         VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    max_interviews INTEGER,
    max_candidates INTEGER,
    max_clients    INTEGER,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO auth_svc.organizations (code, name, type, status)
VALUES ('TESTYANTRA', 'Testyantra', 'LIVE', 'ACTIVE')
ON CONFLICT (code) DO NOTHING;

ALTER TABLE auth_svc.users
    ADD COLUMN IF NOT EXISTS org_code VARCHAR(64) NOT NULL DEFAULT 'TESTYANTRA';

CREATE INDEX IF NOT EXISTS idx_users_org_code ON auth_svc.users (org_code);
