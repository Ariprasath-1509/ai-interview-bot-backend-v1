-- Migrate old roles to new ones (runs in a separate transaction after V4 is committed)
UPDATE auth_svc.users SET role = 'INTERVIEWER' WHERE role IN ('ENGINEER', 'PRACTICE_LEAD');
UPDATE auth_svc.users SET role = 'HR'          WHERE role = 'TALENT';
