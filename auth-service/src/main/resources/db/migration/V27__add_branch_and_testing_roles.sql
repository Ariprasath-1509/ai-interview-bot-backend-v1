-- Branch segregation: TESTING vs DEVELOPMENT on users, plus testing staff roles

ALTER TYPE auth_svc.user_role ADD VALUE IF NOT EXISTS 'TESTING_ADMIN';
ALTER TYPE auth_svc.user_role ADD VALUE IF NOT EXISTS 'TESTING_RECRUITER';

ALTER TABLE auth_svc.users
    ADD COLUMN IF NOT EXISTS branch VARCHAR(32) NOT NULL DEFAULT 'DEVELOPMENT';

CREATE INDEX IF NOT EXISTS idx_users_branch ON auth_svc.users (branch);

-- Master data for branch dropdowns
INSERT INTO master_data_svc.entries (category, code, label, display_order) VALUES
    ('BRANCH', 'DEVELOPMENT', 'Development', 1),
    ('BRANCH', 'TESTING', 'Testing', 2)
ON CONFLICT (category, code) DO NOTHING;
