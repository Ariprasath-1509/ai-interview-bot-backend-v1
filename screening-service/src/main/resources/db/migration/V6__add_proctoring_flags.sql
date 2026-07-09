ALTER TABLE screening_svc.screening_candidates
    ADD COLUMN tab_switch_count INT NOT NULL DEFAULT 0,
    ADD COLUMN proctoring_violation BOOLEAN NOT NULL DEFAULT false;
