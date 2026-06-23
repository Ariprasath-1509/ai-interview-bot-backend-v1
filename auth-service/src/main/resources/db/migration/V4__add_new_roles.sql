-- Only add new enum values in this migration (must be committed before use)
ALTER TYPE auth_svc.user_role ADD VALUE IF NOT EXISTS 'INTERVIEWER';
ALTER TYPE auth_svc.user_role ADD VALUE IF NOT EXISTS 'HR';