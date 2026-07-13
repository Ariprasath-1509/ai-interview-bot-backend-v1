ALTER TABLE interview_svc.interviews
    ADD COLUMN IF NOT EXISTS assessment_type VARCHAR(24) NOT NULL DEFAULT 'CLIENT_INTERVIEW';

COMMENT ON COLUMN interview_svc.interviews.assessment_type IS
    'CLIENT_INTERVIEW (default, JD-based, 4-stage assessment) or ONBOARDING (concept-based, single-pass assessment)';
