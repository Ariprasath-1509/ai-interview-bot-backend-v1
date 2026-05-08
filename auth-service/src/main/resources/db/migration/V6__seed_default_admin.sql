INSERT INTO auth_svc.users (id, email, name, password, role, created_at, updated_at)
VALUES (
    gen_random_uuid()::text,
    'admin@benchreadiness.com',
    'Admin',
    'Admin@123',
    'BENCH_MANAGER',
    NOW(),
    NOW()
) ON CONFLICT (email) DO NOTHING;