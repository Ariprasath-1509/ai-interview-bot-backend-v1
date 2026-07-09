ALTER TABLE screening_svc.screening_candidates
    ADD COLUMN violation_locked BOOLEAN NOT NULL DEFAULT false;
