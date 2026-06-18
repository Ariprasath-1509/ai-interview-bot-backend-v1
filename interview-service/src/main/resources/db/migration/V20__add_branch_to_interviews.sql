-- Branch on interviews for analytics scoping and cross-branch validation audit trail

ALTER TABLE interview_svc.interviews
    ADD COLUMN IF NOT EXISTS branch VARCHAR(32) NOT NULL DEFAULT 'DEVELOPMENT';

ALTER TABLE interview_svc.interviews
    ADD COLUMN IF NOT EXISTS client_id UUID;

CREATE INDEX IF NOT EXISTS idx_interviews_branch ON interview_svc.interviews (branch);
