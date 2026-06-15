ALTER TABLE interview_svc.interviews
    ADD COLUMN include_programming_questions BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN interview_svc.interviews.include_programming_questions IS
    'When false, interview uses theory/verbal questions only (no forced coding slot or code editor).';
