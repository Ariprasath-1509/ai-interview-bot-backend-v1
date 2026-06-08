-- Tier-A master data: editable lookup values (replaces hardcoded PostgreSQL enums for candidate fields)

CREATE SCHEMA IF NOT EXISTS master_data_svc;

CREATE TABLE IF NOT EXISTS master_data_svc.entries (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category      VARCHAR(64)  NOT NULL,
    code          VARCHAR(128) NOT NULL,
    label         VARCHAR(255) NOT NULL,
    display_order INT          NOT NULL DEFAULT 0,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    metadata      JSONB,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_master_data_category_code UNIQUE (category, code)
);

CREATE INDEX IF NOT EXISTS idx_master_data_entries_category
    ON master_data_svc.entries (category, active, display_order);

-- Skill sets
INSERT INTO master_data_svc.entries (category, code, label, display_order) VALUES
    ('SKILL_SET', 'JAVA_SB', 'Java Spring Boot', 1),
    ('SKILL_SET', 'JFSR', 'Java Full Stack React', 2),
    ('SKILL_SET', 'REACT_JS', 'React.js', 3),
    ('SKILL_SET', 'ANGULAR', 'Angular', 4),
    ('SKILL_SET', 'PYTHON', 'Python', 5),
    ('SKILL_SET', 'QA_ENGINEER', 'QA Engineer', 6),
    ('SKILL_SET', 'PLAYWRIGHT_AUTOMATION', 'Playwright Automation', 7)
ON CONFLICT (category, code) DO NOTHING;

-- Candidate pipeline statuses
INSERT INTO master_data_svc.entries (category, code, label, display_order) VALUES
    ('CANDIDATE_STATUS', 'RFD', 'Ready for Deployment', 1),
    ('CANDIDATE_STATUS', 'WFD', 'Waiting for Deployment', 2),
    ('CANDIDATE_STATUS', 'DOB', 'Deploy Observe on Bill', 3),
    ('CANDIDATE_STATUS', 'TRAINING', 'In Training', 4),
    ('CANDIDATE_STATUS', 'DEPLOYED', 'Deployed', 5)
ON CONFLICT (category, code) DO NOTHING;

-- Candidate sources
INSERT INTO master_data_svc.entries (category, code, label, display_order) VALUES
    ('CANDIDATE_SOURCE', 'B2B', 'B2B', 1),
    ('CANDIDATE_SOURCE', 'BENCH', 'Bench', 2),
    ('CANDIDATE_SOURCE', 'MARKET', 'Market', 3)
ON CONFLICT (category, code) DO NOTHING;

-- Candidate ratings
INSERT INTO master_data_svc.entries (category, code, label, display_order) VALUES
    ('CANDIDATE_RATING', 'ASSET', 'Asset', 1),
    ('CANDIDATE_RATING', 'MEDIUM', 'Medium', 2),
    ('CANDIDATE_RATING', 'LIABILITY', 'Liability', 3)
ON CONFLICT (category, code) DO NOTHING;

-- Admin sources (metadata maps to allowed candidate sources)
INSERT INTO master_data_svc.entries (category, code, label, display_order, metadata) VALUES
    ('ADMIN_SOURCE', 'BENCH', 'Bench Admin', 1, '{"allowedCandidateSources": ["B2B", "BENCH"]}'),
    ('ADMIN_SOURCE', 'BD', 'BD Admin', 2, '{"allowedCandidateSources": ["B2B"]}'),
    ('ADMIN_SOURCE', 'RECRUITMENT', 'Recruitment Admin', 3, '{"allowedCandidateSources": ["MARKET"]}')
ON CONFLICT (category, code) DO NOTHING;

-- Interview rounds (question bank)
INSERT INTO master_data_svc.entries (category, code, label, display_order) VALUES
    ('INTERVIEW_ROUND', 'SCREENING', 'Screening', 1),
    ('INTERVIEW_ROUND', 'L1', 'Level 1', 2),
    ('INTERVIEW_ROUND', 'L2', 'Level 2', 3),
    ('INTERVIEW_ROUND', 'L3', 'Level 3', 4),
    ('INTERVIEW_ROUND', 'L4', 'Level 4', 5),
    ('INTERVIEW_ROUND', 'L5', 'Level 5', 6),
    ('INTERVIEW_ROUND', 'L6', 'Level 6', 7),
    ('INTERVIEW_ROUND', 'L7', 'Level 7', 8),
    ('INTERVIEW_ROUND', 'L8', 'Level 8', 9),
    ('INTERVIEW_ROUND', 'L9', 'Level 9', 10),
    ('INTERVIEW_ROUND', 'L10', 'Level 10', 11),
    ('INTERVIEW_ROUND', 'L11', 'Level 11', 12),
    ('INTERVIEW_ROUND', 'L12', 'Level 12', 13),
    ('INTERVIEW_ROUND', 'HR', 'HR Round', 14),
    ('INTERVIEW_ROUND', 'F2F', 'Face to Face', 15),
    ('INTERVIEW_ROUND', 'TECHNICAL', 'Technical', 16),
    ('INTERVIEW_ROUND', 'MANAGERIAL', 'Managerial', 17)
ON CONFLICT (category, code) DO NOTHING;

-- Question types
INSERT INTO master_data_svc.entries (category, code, label, display_order) VALUES
    ('QUESTION_TYPE', 'TECHNICAL', 'Technical', 1),
    ('QUESTION_TYPE', 'BEHAVIORAL', 'Behavioral', 2),
    ('QUESTION_TYPE', 'CODING', 'Coding', 3)
ON CONFLICT (category, code) DO NOTHING;

-- Difficulty levels
INSERT INTO master_data_svc.entries (category, code, label, display_order) VALUES
    ('DIFFICULTY', 'EASY', 'Easy', 1),
    ('DIFFICULTY', 'MEDIUM', 'Medium', 2),
    ('DIFFICULTY', 'HARD', 'Hard', 3)
ON CONFLICT (category, code) DO NOTHING;

-- Category interview types (question bank categories)
INSERT INTO master_data_svc.entries (category, code, label, display_order) VALUES
    ('CATEGORY_INTERVIEW_TYPE', 'backend', 'Backend', 1),
    ('CATEGORY_INTERVIEW_TYPE', 'frontend', 'Frontend', 2),
    ('CATEGORY_INTERVIEW_TYPE', 'shared', 'Shared', 3)
ON CONFLICT (category, code) DO NOTHING;

-- Client position requirement sources
INSERT INTO master_data_svc.entries (category, code, label, display_order) VALUES
    ('POSITION_SOURCE', 'BENCH_B2B', 'Bench / B2B', 1),
    ('POSITION_SOURCE', 'MARKET', 'Market', 2)
ON CONFLICT (category, code) DO NOTHING;

-- Migrate legacy enum value while columns are still PostgreSQL enums
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_type t
        JOIN pg_namespace n ON n.oid = t.typnamespace
        WHERE n.nspname = 'auth_svc'
          AND t.typname = 'candidate_status'
          AND t.typtype = 'e'
    ) THEN
        UPDATE auth_svc.users u
        SET candidate_status = 'WFD'::auth_svc.candidate_status
        WHERE u.candidate_status = 'NOT_RFD'::auth_svc.candidate_status;
    END IF;
END $$;

-- Partial index WHERE clause uses enum typing; drop before converting candidate_status
DROP INDEX IF EXISTS auth_svc.idx_users_deployed_status;

-- source / rating: column names differ from enum type names — safe to alter in place
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'auth_svc' AND table_name = 'users'
          AND column_name = 'source' AND udt_schema = 'auth_svc' AND udt_name = 'candidate_source'
    ) THEN
        ALTER TABLE auth_svc.users
            ALTER COLUMN source TYPE VARCHAR(128) USING (source::text);
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'auth_svc' AND table_name = 'users'
          AND column_name = 'rating' AND udt_schema = 'auth_svc' AND udt_name = 'candidate_rating'
    ) THEN
        ALTER TABLE auth_svc.users
            ALTER COLUMN rating TYPE VARCHAR(128) USING (rating::text);
    END IF;
END $$;

-- candidate_status / skill_set / admin_source: column name equals enum type name — use add/copy/drop/rename
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'auth_svc' AND table_name = 'users'
          AND column_name = 'candidate_status' AND udt_schema = 'auth_svc' AND udt_name = 'candidate_status'
    ) THEN
        ALTER TABLE auth_svc.users ADD COLUMN candidate_status_txt VARCHAR(128);
        UPDATE auth_svc.users u SET candidate_status_txt = text(u.candidate_status);
        ALTER TABLE auth_svc.users DROP COLUMN candidate_status;
        ALTER TABLE auth_svc.users RENAME COLUMN candidate_status_txt TO candidate_status;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'auth_svc' AND table_name = 'users'
          AND column_name = 'skill_set' AND udt_schema = 'auth_svc' AND udt_name = 'skill_set'
    ) THEN
        ALTER TABLE auth_svc.users ADD COLUMN skill_set_txt VARCHAR(128);
        UPDATE auth_svc.users u SET skill_set_txt = text(u.skill_set);
        ALTER TABLE auth_svc.users DROP COLUMN skill_set;
        ALTER TABLE auth_svc.users RENAME COLUMN skill_set_txt TO skill_set;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'auth_svc' AND table_name = 'users'
          AND column_name = 'admin_source' AND udt_schema = 'auth_svc' AND udt_name = 'admin_source'
    ) THEN
        ALTER TABLE auth_svc.users ADD COLUMN admin_source_txt VARCHAR(128);
        UPDATE auth_svc.users u SET admin_source_txt = text(u.admin_source);
        ALTER TABLE auth_svc.users DROP COLUMN admin_source;
        ALTER TABLE auth_svc.users RENAME COLUMN admin_source_txt TO admin_source;
    END IF;
END $$;

-- Restore partial index on varchar column
CREATE INDEX IF NOT EXISTS idx_users_deployed_status
    ON auth_svc.users (candidate_status)
    WHERE candidate_status = 'DEPLOYED';
