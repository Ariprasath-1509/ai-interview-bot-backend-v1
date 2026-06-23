-- Branch segregation: TESTING vs DEVELOPMENT on client requirements

ALTER TABLE interview_svc.clients
    ADD COLUMN IF NOT EXISTS branch VARCHAR(32) NOT NULL DEFAULT 'DEVELOPMENT';

CREATE INDEX IF NOT EXISTS idx_clients_branch ON interview_svc.clients (branch);
