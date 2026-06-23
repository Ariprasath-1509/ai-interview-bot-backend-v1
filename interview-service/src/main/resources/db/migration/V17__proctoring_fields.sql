ALTER TABLE interview_svc.interviews
    ADD COLUMN proctoring_events_json TEXT,
    ADD COLUMN proctoring_score INTEGER,
    ADD COLUMN proctoring_snapshots_json TEXT;

COMMENT ON COLUMN interview_svc.interviews.proctoring_events_json IS 'Video proctoring event log and summary JSON';
COMMENT ON COLUMN interview_svc.interviews.proctoring_score IS 'Integrity score 0-100 from video proctoring';
COMMENT ON COLUMN interview_svc.interviews.proctoring_snapshots_json IS 'Metadata for violation snapshot images';
