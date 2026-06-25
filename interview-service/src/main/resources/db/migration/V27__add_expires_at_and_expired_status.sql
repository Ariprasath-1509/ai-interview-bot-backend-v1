-- Add expires_at column and EXPIRED status for interview time-window feature

ALTER TABLE interview_svc.interviews ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP WITH TIME ZONE NULL;

ALTER TYPE interview_svc.interview_status ADD VALUE IF NOT EXISTS 'EXPIRED';
