ALTER TABLE review_svc.scores
    ADD COLUMN IF NOT EXISTS strengths  TEXT,
    ADD COLUMN IF NOT EXISTS weaknesses TEXT;
