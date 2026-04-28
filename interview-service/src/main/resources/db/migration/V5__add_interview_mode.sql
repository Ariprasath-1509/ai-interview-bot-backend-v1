-- Add interview_mode enum type and column to interviews table
CREATE TYPE interview_svc.interview_mode AS ENUM ('SCREENING', 'L1', 'L2', 'L3', 'L4');

ALTER TABLE interview_svc.interviews 
ADD COLUMN interview_mode interview_svc.interview_mode DEFAULT 'SCREENING' NOT NULL;