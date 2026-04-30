ALTER TABLE interview_svc.interviews
    ADD COLUMN IF NOT EXISTS created_by_user_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS custom_duration_minutes INT;
