ALTER TABLE interview_svc.interviews
    ADD COLUMN IF NOT EXISTS checkpoint_json TEXT;
