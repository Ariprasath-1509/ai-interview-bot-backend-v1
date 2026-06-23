-- Change score constraint from 1-5 to 1-10 scale
ALTER TABLE review_svc.scores DROP CONSTRAINT IF EXISTS scores_value_check;
ALTER TABLE review_svc.scores ADD CONSTRAINT scores_value_check CHECK (value BETWEEN 1 AND 10);
