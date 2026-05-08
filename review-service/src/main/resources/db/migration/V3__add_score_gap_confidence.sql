ALTER TABLE review_svc.scores
    ADD COLUMN IF NOT EXISTS gap        TEXT,
    ADD COLUMN IF NOT EXISTS confidence VARCHAR(10);