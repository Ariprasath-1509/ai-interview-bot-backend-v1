-- Add new role enum values
ALTER TYPE auth_svc.user_role ADD VALUE IF NOT EXISTS 'SUPER_ADMIN';
ALTER TYPE auth_svc.user_role ADD VALUE IF NOT EXISTS 'RECRUITER';