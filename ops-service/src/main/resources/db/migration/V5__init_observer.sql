CREATE SCHEMA IF NOT EXISTS observer_svc;

CREATE TABLE IF NOT EXISTS observer_svc.observer_events (
    id                VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    interview_id      VARCHAR(36) NOT NULL,
    observer_user_id  VARCHAR(36) NOT NULL,
    kind              VARCHAR(50) NOT NULL,
    payload_json      TEXT NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_observer_events_interview_id ON observer_svc.observer_events(interview_id);
CREATE INDEX IF NOT EXISTS idx_observer_events_created_at   ON observer_svc.observer_events(created_at DESC);
