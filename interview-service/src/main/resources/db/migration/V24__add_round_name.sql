ALTER TABLE interview_svc.interviews
    ADD COLUMN IF NOT EXISTS round_name VARCHAR(128);

COMMENT ON COLUMN interview_svc.interviews.round_name IS
    'Client-facing round label for reports (e.g. Hands-On, Technical Screen)';
