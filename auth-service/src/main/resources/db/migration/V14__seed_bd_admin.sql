-- Seed Bench Admin (manages BENCH candidates)
INSERT INTO auth_svc.users (id, email, name, password, role, admin_source, created_at, updated_at)
VALUES (gen_random_uuid()::text, 'benchadmin@benchreadiness.com', 'Bench Admin', 'Admin@123', 'ADMIN', 'BENCH', NOW(), NOW())
ON CONFLICT (email) DO NOTHING;

-- Seed BD Admin (manages B2B candidates)
INSERT INTO auth_svc.users (id, email, name, password, role, admin_source, created_at, updated_at)
VALUES (gen_random_uuid()::text, 'bdadmin@benchreadiness.com', 'BD Admin', 'Admin@123', 'ADMIN', 'BD', NOW(), NOW())
ON CONFLICT (email) DO NOTHING;

-- Seed Recruitment Admin (manages MARKET candidates)
INSERT INTO auth_svc.users (id, email, name, password, role, admin_source, created_at, updated_at)
VALUES (gen_random_uuid()::text, 'recruitmentadmin@benchreadiness.com', 'Recruitment Admin', 'Admin@123', 'ADMIN', 'RECRUITMENT', NOW(), NOW())
ON CONFLICT (email) DO NOTHING;