-- Update candidate_status enum to include WFD and DOB
-- PostgreSQL requires enum additions to be in separate transactions
-- This migration adds new values and updates existing data

-- Add new enum values (these will be committed automatically)
ALTER TYPE auth_svc.candidate_status ADD VALUE IF NOT EXISTS 'WFD';
ALTER TYPE auth_svc.candidate_status ADD VALUE IF NOT EXISTS 'DOB';