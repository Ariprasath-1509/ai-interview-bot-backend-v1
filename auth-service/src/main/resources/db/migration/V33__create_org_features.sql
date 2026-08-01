-- Per-org feature entitlements. A feature with no row here is enabled by default, so existing
-- orgs keep every feature until SUPER_ADMIN explicitly disables one for a tenant.
CREATE TABLE IF NOT EXISTS auth_svc.org_features (
    id           VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    org_code     VARCHAR(64) NOT NULL,
    feature_key  VARCHAR(32) NOT NULL,
    enabled      BOOLEAN NOT NULL DEFAULT true,
    UNIQUE (org_code, feature_key)
);

CREATE INDEX IF NOT EXISTS idx_org_features_org_code ON auth_svc.org_features (org_code);
