-- Add DEPLOYED status to candidate_status enum
ALTER TYPE auth_svc.candidate_status ADD VALUE IF NOT EXISTS 'DEPLOYED';
