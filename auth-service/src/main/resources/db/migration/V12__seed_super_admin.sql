-- Ensure super admin exists (update existing seeded account or insert fresh)
UPDATE auth_svc.users SET role = 'SUPER_ADMIN' WHERE email = 'admin@benchreadiness.com';

INSERT INTO auth_svc.users (id, email, name, password, role, created_at, updated_at)
VALUES (gen_random_uuid()::text, 'admin@benchreadiness.com', 'Super Admin', 'Admin@123', 'SUPER_ADMIN', NOW(), NOW())
ON CONFLICT (email) DO NOTHING;
