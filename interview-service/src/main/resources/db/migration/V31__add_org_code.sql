-- Organization (tenant) separation — mirrors the existing `branch` column exactly, on the same two
-- entities that already carry branch (clients, interviews). Engineer/JD/plan scope is derived from
-- their owning client/interview, same precedent as branch.

ALTER TABLE interview_svc.clients
    ADD COLUMN IF NOT EXISTS org_code VARCHAR(64) NOT NULL DEFAULT 'TESTYANTRA';

ALTER TABLE interview_svc.interviews
    ADD COLUMN IF NOT EXISTS org_code VARCHAR(64) NOT NULL DEFAULT 'TESTYANTRA';

CREATE INDEX IF NOT EXISTS idx_clients_org_code ON interview_svc.clients (org_code);
CREATE INDEX IF NOT EXISTS idx_interviews_org_code ON interview_svc.interviews (org_code);
