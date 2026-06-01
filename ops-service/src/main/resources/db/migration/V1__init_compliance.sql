CREATE SCHEMA IF NOT EXISTS compliance_svc;

CREATE TABLE compliance_svc.audit_logs (
    id           VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    actor_id     VARCHAR(36) NOT NULL,
    actor_role   VARCHAR(50) NOT NULL,
    action       VARCHAR(100) NOT NULL,
    resource     VARCHAR(100) NOT NULL,
    resource_id  VARCHAR(36),
    detail       TEXT,
    ip_address   VARCHAR(45),
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_actor_id    ON compliance_svc.audit_logs(actor_id);
CREATE INDEX idx_audit_logs_resource_id ON compliance_svc.audit_logs(resource_id);
CREATE INDEX idx_audit_logs_created_at  ON compliance_svc.audit_logs(created_at DESC);

CREATE TABLE compliance_svc.retention_policies (
    id              VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    region          VARCHAR(100) NOT NULL UNIQUE,
    transcript_days INT NOT NULL DEFAULT 365,
    audio_days      INT NOT NULL DEFAULT 90,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Default global policy
INSERT INTO compliance_svc.retention_policies (id, region, transcript_days, audio_days)
VALUES (gen_random_uuid()::text, 'global', 365, 90);