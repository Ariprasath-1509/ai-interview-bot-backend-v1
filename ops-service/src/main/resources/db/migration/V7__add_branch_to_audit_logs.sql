ALTER TABLE compliance_svc.audit_logs
    ADD COLUMN IF NOT EXISTS branch VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_audit_logs_branch ON compliance_svc.audit_logs (branch);
