ALTER TABLE interview_svc.interview_plans
    ADD COLUMN IF NOT EXISTS rubric_json        TEXT,
    ADD COLUMN IF NOT EXISTS candidate_profile_json TEXT;
