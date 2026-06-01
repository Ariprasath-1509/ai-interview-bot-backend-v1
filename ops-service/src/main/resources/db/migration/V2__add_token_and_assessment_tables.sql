CREATE TABLE compliance_svc.token_usage (
    id                  VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    interview_id        VARCHAR(36) NOT NULL,
    operation_type      VARCHAR(50) NOT NULL,
    model_used          VARCHAR(100) NOT NULL,
    prompt_tokens       INT NOT NULL DEFAULT 0,
    completion_tokens   INT NOT NULL DEFAULT 0,
    total_tokens        INT NOT NULL DEFAULT 0,
    estimated_cost_usd  NUMERIC(10,6) DEFAULT 0,
    created_by_user_id  VARCHAR(36),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_token_usage_interview_id ON compliance_svc.token_usage(interview_id);
CREATE INDEX idx_token_usage_created_at   ON compliance_svc.token_usage(created_at DESC);

CREATE TABLE compliance_svc.daily_token_limits (
    id                  VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    organization_id     VARCHAR(100) DEFAULT 'default',
    daily_limit         INT NOT NULL DEFAULT 100000,
    warning_threshold   INT NOT NULL DEFAULT 80000,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

INSERT INTO compliance_svc.daily_token_limits (id, organization_id, daily_limit, warning_threshold)
VALUES (gen_random_uuid()::text, 'default', 100000, 80000);

CREATE TABLE compliance_svc.assessment_responses (
    id                  VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    interview_id        VARCHAR(36) NOT NULL UNIQUE,
    assessment_json     TEXT NOT NULL,
    tokens_used         INT NOT NULL DEFAULT 0,
    assessment_source   VARCHAR(100) NOT NULL DEFAULT 'claude-two-pass',
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_assessment_responses_interview_id ON compliance_svc.assessment_responses(interview_id);

CREATE TABLE compliance_svc.interview_token_summary (
    id                  VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    interview_id        VARCHAR(36) NOT NULL UNIQUE,
    total_tokens        INT NOT NULL DEFAULT 0,
    total_cost_usd      NUMERIC(10,6) DEFAULT 0,
    question_tokens     INT NOT NULL DEFAULT 0,
    assessment_tokens   INT NOT NULL DEFAULT 0,
    rubric_tokens       INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_interview_token_summary_interview_id ON compliance_svc.interview_token_summary(interview_id);