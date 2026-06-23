CREATE SCHEMA IF NOT EXISTS interview_svc;

CREATE TYPE interview_svc.interview_status AS ENUM (
    'DRAFT', 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'REVIEW_PENDING', 'SIGNED_OFF'
);

CREATE TYPE interview_svc.readiness_verdict AS ENUM (
    'READY', 'NEEDS_1_WEEK_PREP', 'NEEDS_RESKILLING', 'MISMATCH_WITH_JD'
);

CREATE TABLE interview_svc.engineers (
    id                VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id           VARCHAR(36) NOT NULL UNIQUE,
    grade_level       VARCHAR(100),
    years_experience  INT,
    last_engagement   VARCHAR(255),
    primary_track     VARCHAR(100),
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE interview_svc.job_descriptions (
    id         VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    title      VARCHAR(255),
    source     VARCHAR(50),
    text       TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE interview_svc.interview_plans (
    id                VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    engineer_id       VARCHAR(36) NOT NULL REFERENCES interview_svc.engineers(id) ON DELETE CASCADE,
    jd_id             VARCHAR(36) NOT NULL REFERENCES interview_svc.job_descriptions(id) ON DELETE CASCADE,
    slots_json        TEXT NOT NULL,
    gap_map_json      TEXT NOT NULL,
    created_by_user_id VARCHAR(36) NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE interview_svc.interviews (
    id                VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    engineer_id       VARCHAR(36) NOT NULL REFERENCES interview_svc.engineers(id) ON DELETE CASCADE,
    jd_id             VARCHAR(36) NOT NULL REFERENCES interview_svc.job_descriptions(id) ON DELETE CASCADE,
    plan_id           VARCHAR(36) UNIQUE REFERENCES interview_svc.interview_plans(id) ON DELETE SET NULL,
    status            interview_svc.interview_status NOT NULL DEFAULT 'DRAFT',
    scheduled_at      TIMESTAMP,
    started_at        TIMESTAMP,
    ended_at          TIMESTAMP,
    transcript_json   TEXT,
    audio_storage_key VARCHAR(500),
    rubric_role_type  VARCHAR(100),
    rubric_level      VARCHAR(100),
    proposed_verdict  interview_svc.readiness_verdict,
    final_verdict     interview_svc.readiness_verdict,
    sign_off_by_user_id VARCHAR(36),
    sign_off_note     TEXT,
    signed_off_at     TIMESTAMP,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE interview_svc.scores (
    id           VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    interview_id VARCHAR(36) NOT NULL REFERENCES interview_svc.interviews(id) ON DELETE CASCADE,
    dimension    VARCHAR(100) NOT NULL,
    value        INT NOT NULL CHECK (value BETWEEN 1 AND 5),
    rationale    TEXT,
    evidence     TEXT,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);