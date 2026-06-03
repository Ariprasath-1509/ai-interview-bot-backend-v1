CREATE TABLE IF NOT EXISTS interview_svc.interview_questions (
    id              BIGSERIAL PRIMARY KEY,
    interview_id    VARCHAR(36) NOT NULL REFERENCES interview_svc.interviews(id) ON DELETE CASCADE,
    slot_number     INT NOT NULL,
    question_text   TEXT NOT NULL,
    candidate_answer TEXT,
    difficulty_level VARCHAR(50),
    question_type   VARCHAR(50),
    question_bank_id VARCHAR(100),
    source          VARCHAR(50),
    asked_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    answered_at     TIMESTAMP,
    CONSTRAINT uq_interview_slot UNIQUE (interview_id, slot_number)
);

CREATE TABLE IF NOT EXISTS interview_svc.question_tags (
    question_id BIGINT NOT NULL REFERENCES interview_svc.interview_questions(id) ON DELETE CASCADE,
    tag         VARCHAR(100) NOT NULL,
    PRIMARY KEY (question_id, tag)
);

CREATE INDEX IF NOT EXISTS idx_interview_questions_interview_id
    ON interview_svc.interview_questions(interview_id);
