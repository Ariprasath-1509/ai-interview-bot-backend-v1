ALTER TABLE interview_svc.interviews
ADD COLUMN recording_path TEXT;

COMMENT ON COLUMN interview_svc.interviews.recording_path IS 'Server-side path to the stored audio recording (.webm) for this interview session';
