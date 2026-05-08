CREATE SCHEMA IF NOT EXISTS auth_svc;

CREATE TYPE auth_svc.user_role AS ENUM (
    'ENGINEER', 'BENCH_MANAGER', 'TALENT', 'PRACTICE_LEAD', 'COMPLIANCE'
);

CREATE TABLE auth_svc.users (
    id          VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    email       VARCHAR(255) UNIQUE,
    name        VARCHAR(255),
    role        auth_svc.user_role NOT NULL DEFAULT 'ENGINEER',
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);