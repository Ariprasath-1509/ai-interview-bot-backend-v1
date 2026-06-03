ALTER TABLE questionbank_svc.questions
    ADD COLUMN IF NOT EXISTS question_type VARCHAR(32) NOT NULL DEFAULT 'TECHNICAL';

CREATE INDEX IF NOT EXISTS idx_questions_type ON questionbank_svc.questions(question_type);

-- Tag DSA category questions as coding by default
UPDATE questionbank_svc.questions q
SET question_type = 'CODING'
FROM questionbank_svc.categories c
WHERE q.category_id = c.id
  AND (c.name = 'DSA' OR c.interview_type = 'shared' AND c.name ILIKE '%algorithm%');
