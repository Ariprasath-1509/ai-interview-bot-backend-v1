-- Add active flag to users table for market candidate credential lifecycle
ALTER TABLE auth_svc.users ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

-- Market candidates start inactive until their interview is scheduled
UPDATE auth_svc.users SET active = FALSE WHERE source = 'MARKET' AND active = TRUE;
-- Reset to true — we only deactivate on future market candidates; existing ones remain active
UPDATE auth_svc.users SET active = TRUE WHERE source = 'MARKET';
