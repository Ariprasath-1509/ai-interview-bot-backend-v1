-- Add MARKET to candidate_source enum
ALTER TYPE auth_svc.candidate_source ADD VALUE IF NOT EXISTS 'MARKET';

-- Admin source enum: BENCH admin manages B2B+BENCH candidates, RECRUITMENT admin manages MARKET candidates
CREATE TYPE auth_svc.admin_source AS ENUM ('BENCH', 'RECRUITMENT');

-- Add admin_source column to users (only used for ADMIN role)
ALTER TABLE auth_svc.users ADD COLUMN IF NOT EXISTS admin_source auth_svc.admin_source;