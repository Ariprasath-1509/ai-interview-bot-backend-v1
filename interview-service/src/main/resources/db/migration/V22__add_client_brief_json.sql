ALTER TABLE interview_svc.interviews
    ADD COLUMN IF NOT EXISTS client_brief_json TEXT;

COMMENT ON COLUMN interview_svc.interviews.client_brief_json IS
    'Staff-edited client-facing evaluation brief (JSON). Not visible to candidates.';
