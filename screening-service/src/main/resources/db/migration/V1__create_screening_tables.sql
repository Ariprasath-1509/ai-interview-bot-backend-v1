CREATE SCHEMA IF NOT EXISTS screening_svc;

CREATE TABLE screening_svc.screening_batches (
    id CHAR(36) PRIMARY KEY,
    language VARCHAR(64) NOT NULL,
    concept_scope TEXT,
    assigner_user_id CHAR(36) NOT NULL,
    assigner_name VARCHAR(255),
    assigner_email VARCHAR(255) NOT NULL,
    deadline TIMESTAMP NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    report_sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE screening_svc.screening_questions (
    id CHAR(36) PRIMARY KEY,
    batch_id CHAR(36) NOT NULL REFERENCES screening_svc.screening_batches(id) ON DELETE CASCADE,
    question_type VARCHAR(32) NOT NULL,
    prompt TEXT NOT NULL,
    options_json TEXT,
    reference_answer TEXT,
    marks INT NOT NULL,
    display_index INT NOT NULL
);
CREATE INDEX idx_screening_questions_batch ON screening_svc.screening_questions(batch_id);

CREATE TABLE screening_svc.screening_candidates (
    id CHAR(36) PRIMARY KEY,
    batch_id CHAR(36) NOT NULL REFERENCES screening_svc.screening_batches(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    token VARCHAR(64) NOT NULL UNIQUE,
    shuffle_seed BIGINT NOT NULL,
    stage VARCHAR(32) NOT NULL DEFAULT 'ROUND1_PENDING',
    round1_score DOUBLE PRECISION,
    round1_started_at TIMESTAMP,
    round1_submitted_at TIMESTAMP,
    round2_started_at TIMESTAMP,
    round2_strengths TEXT,
    round2_weaknesses TEXT,
    round2_practical TEXT,
    round2_improvements TEXT,
    round2_result VARCHAR(32),
    round2_recorded_by CHAR(36),
    round2_recorded_at TIMESTAMP,
    round3_started_at TIMESTAMP,
    round3_strengths TEXT,
    round3_weaknesses TEXT,
    round3_practical TEXT,
    round3_improvements TEXT,
    round3_result VARCHAR(32),
    round3_recorded_by CHAR(36),
    round3_recorded_at TIMESTAMP,
    converted_user_id CHAR(36),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_screening_candidates_batch ON screening_svc.screening_candidates(batch_id);
CREATE INDEX idx_screening_candidates_stage ON screening_svc.screening_candidates(stage);

CREATE TABLE screening_svc.screening_answers (
    id CHAR(36) PRIMARY KEY,
    candidate_id CHAR(36) NOT NULL REFERENCES screening_svc.screening_candidates(id) ON DELETE CASCADE,
    question_id CHAR(36) NOT NULL REFERENCES screening_svc.screening_questions(id) ON DELETE CASCADE,
    raw_answer TEXT,
    score DOUBLE PRECISION,
    ai_feedback TEXT,
    graded_at TIMESTAMP
);
CREATE INDEX idx_screening_answers_candidate ON screening_svc.screening_answers(candidate_id);
