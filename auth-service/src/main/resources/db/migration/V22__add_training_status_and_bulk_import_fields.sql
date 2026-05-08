-- Add TRAINING status to candidate_status enum
ALTER TYPE auth_svc.candidate_status ADD VALUE IF NOT EXISTS 'TRAINING';

-- Add new candidate fields for bulk import
ALTER TABLE auth_svc.users ADD COLUMN IF NOT EXISTS batch_mentor VARCHAR(255);
ALTER TABLE auth_svc.users ADD COLUMN IF NOT EXISTS interview_mentor_name VARCHAR(255);
ALTER TABLE auth_svc.users ADD COLUMN IF NOT EXISTS client_name VARCHAR(255);