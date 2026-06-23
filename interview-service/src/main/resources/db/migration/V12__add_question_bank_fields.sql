-- Add question bank integration fields to interviews table
ALTER TABLE interview_svc.interviews
ADD COLUMN question_bank_questions_json TEXT,
ADD COLUMN used_question_ids TEXT DEFAULT '';

COMMENT ON COLUMN interview_svc.interviews.question_bank_questions_json IS 'JSON array of questions fetched from question bank service at interview creation';
COMMENT ON COLUMN interview_svc.interviews.used_question_ids IS 'Comma-separated UUIDs of questions already asked during interview';
