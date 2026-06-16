-- Audit log display and change-tracking fields (entity expects these; V1 only had actor_id)

ALTER TABLE compliance_svc.audit_logs ADD COLUMN IF NOT EXISTS actor_name VARCHAR(255);
ALTER TABLE compliance_svc.audit_logs ADD COLUMN IF NOT EXISTS old_value TEXT;
ALTER TABLE compliance_svc.audit_logs ADD COLUMN IF NOT EXISTS new_value TEXT;
