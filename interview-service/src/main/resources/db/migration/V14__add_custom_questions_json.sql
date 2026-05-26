-- Add custom_questions_json column to interviews table
ALTER TABLE interview_svc.interviews
ADD COLUMN custom_questions_json TEXT;

COMMENT ON COLUMN interview_svc.interviews.custom_questions_json IS 'JSON array of custom questions provided by admin during interview creation';
