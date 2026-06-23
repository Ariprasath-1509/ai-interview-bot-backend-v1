ALTER TABLE interview_svc.interviews
    ADD COLUMN IF NOT EXISTS assessment_run_id VARCHAR(36);

COMMENT ON COLUMN interview_svc.interviews.assessment_run_id IS 'UUID per async assessment run — prevents stale COMPLETED results on re-assess';
