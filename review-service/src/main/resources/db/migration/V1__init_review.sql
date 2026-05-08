CREATE SCHEMA IF NOT EXISTS review_svc;

CREATE TYPE review_svc.readiness_verdict AS ENUM (
    'READY', 'NEEDS_1_WEEK_PREP', 'NEEDS_RESKILLING', 'MISMATCH_WITH_JD'
);

CREATE TABLE review_svc.scores (
    id           VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    interview_id VARCHAR(36) NOT NULL,
    dimension    VARCHAR(100) NOT NULL,
    value        INT NOT NULL CHECK (value BETWEEN 1 AND 5),
    rationale    TEXT,
    evidence     TEXT,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_scores_interview_id ON review_svc.scores(interview_id);

CREATE TABLE review_svc.sign_offs (
    id              VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    interview_id    VARCHAR(36) NOT NULL UNIQUE,
    reviewer_user_id VARCHAR(36) NOT NULL,
    final_verdict   review_svc.readiness_verdict NOT NULL,
    note            TEXT NOT NULL,
    signed_off_at   TIMESTAMP NOT NULL DEFAULT NOW()
);