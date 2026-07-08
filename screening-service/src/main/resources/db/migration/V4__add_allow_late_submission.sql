ALTER TABLE screening_svc.screening_candidates
    ADD COLUMN allow_late_submission BOOLEAN NOT NULL DEFAULT false;
