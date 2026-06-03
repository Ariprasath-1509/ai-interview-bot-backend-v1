ALTER TABLE interview_svc.interviews
    ADD COLUMN assessment_status VARCHAR(32),
    ADD COLUMN assessment_error TEXT,
    ADD COLUMN assessment_result_json TEXT,
    ADD COLUMN recording_bytes BIGINT;

COMMENT ON COLUMN interview_svc.interviews.assessment_status IS 'NOT_STARTED, PROCESSING, COMPLETED, FAILED';
COMMENT ON COLUMN interview_svc.interviews.assessment_result_json IS 'Cached AI assessment JSON for async polling across service restarts';
