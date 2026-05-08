-- Candidate profile enums
CREATE TYPE auth_svc.candidate_source AS ENUM ('B2B', 'BENCH');
CREATE TYPE auth_svc.candidate_status AS ENUM ('RFD', 'NOT_RFD');
CREATE TYPE auth_svc.candidate_rating AS ENUM ('ASSET', 'MEDIUM', 'LIABILITY');
CREATE TYPE auth_svc.skill_set AS ENUM ('JAVA_SB', 'JFSR', 'REACT_JS');

-- Add candidate profile columns to users table
ALTER TABLE auth_svc.users
    ADD COLUMN IF NOT EXISTS contact_number VARCHAR(20),
    ADD COLUMN IF NOT EXISTS official_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS personal_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS batch VARCHAR(100),
    ADD COLUMN IF NOT EXISTS source auth_svc.candidate_source,
    ADD COLUMN IF NOT EXISTS candidate_status auth_svc.candidate_status,
    ADD COLUMN IF NOT EXISTS rating auth_svc.candidate_rating,
    ADD COLUMN IF NOT EXISTS skill_set auth_svc.skill_set,
    ADD COLUMN IF NOT EXISTS yoe_actual NUMERIC(4,1),
    ADD COLUMN IF NOT EXISTS yoe_portrayed NUMERIC(4,1),
    ADD COLUMN IF NOT EXISTS no_of_interviews INT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS yop INT;