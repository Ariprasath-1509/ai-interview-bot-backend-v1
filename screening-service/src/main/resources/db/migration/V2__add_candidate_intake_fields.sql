ALTER TABLE screening_svc.screening_candidates
    ADD COLUMN contact_number VARCHAR(32),
    ADD COLUMN institute VARCHAR(32),
    ADD COLUMN branch VARCHAR(64),
    ADD COLUMN yop INT,
    ADD COLUMN experience DOUBLE PRECISION;
