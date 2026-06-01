-- Remove duplicate rows, keep only the oldest one
DELETE FROM compliance_svc.daily_token_limits
WHERE id NOT IN (
    SELECT id FROM compliance_svc.daily_token_limits
    WHERE organization_id = 'default'
    ORDER BY created_at ASC
    LIMIT 1
) AND organization_id = 'default';

-- Add unique constraint to prevent future duplicates
ALTER TABLE compliance_svc.daily_token_limits
    ADD CONSTRAINT uq_daily_token_limits_org_id UNIQUE (organization_id);