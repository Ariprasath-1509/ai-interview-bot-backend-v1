ALTER TABLE interview_svc.interviews
    ADD COLUMN IF NOT EXISTS question_difficulty VARCHAR(16);

COMMENT ON COLUMN interview_svc.interviews.question_difficulty IS
    'Per-round question difficulty override (EASY, MEDIUM, HARD); NULL derives difficulty from interview_mode';
