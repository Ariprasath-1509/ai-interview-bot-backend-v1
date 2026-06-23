-- Migrate old roles to new ones (runs after V9 commits the new enum values)
-- ADMIN → SUPER_ADMIN (existing admins become super admins)
UPDATE auth_svc.users SET role = 'SUPER_ADMIN' WHERE role = 'ADMIN';
-- BENCH_MANAGER → SUPER_ADMIN (the seeded admin becomes super admin)
UPDATE auth_svc.users SET role = 'SUPER_ADMIN' WHERE role = 'BENCH_MANAGER';
-- INTERVIEWER → RECRUITER
UPDATE auth_svc.users SET role = 'RECRUITER' WHERE role = 'INTERVIEWER';
-- HR → RECRUITER (merge into recruiter)
UPDATE auth_svc.users SET role = 'RECRUITER' WHERE role = 'HR';
-- COMPLIANCE → SUPER_ADMIN (merge into super admin)
UPDATE auth_svc.users SET role = 'SUPER_ADMIN' WHERE role = 'COMPLIANCE';