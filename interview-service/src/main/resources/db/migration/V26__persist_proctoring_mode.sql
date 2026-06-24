ALTER TABLE interview_svc.interviews
    ADD COLUMN IF NOT EXISTS proctoring_mode VARCHAR(8);
