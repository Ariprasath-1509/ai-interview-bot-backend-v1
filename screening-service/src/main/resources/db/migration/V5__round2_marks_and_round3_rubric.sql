ALTER TABLE screening_svc.screening_candidates
    ADD COLUMN round2_marks DOUBLE PRECISION;

ALTER TABLE screening_svc.screening_candidates
    DROP COLUMN IF EXISTS round3_strengths,
    DROP COLUMN IF EXISTS round3_weaknesses,
    DROP COLUMN IF EXISTS round3_practical,
    DROP COLUMN IF EXISTS round3_improvements;

ALTER TABLE screening_svc.screening_candidates
    ADD COLUMN round3_communication INT,
    ADD COLUMN round3_problem_solving INT,
    ADD COLUMN round3_attitude_coachability INT,
    ADD COLUMN round3_learning_agility INT,
    ADD COLUMN round3_teamwork INT,
    ADD COLUMN round3_body_language INT,
    ADD COLUMN round3_concluding_comments TEXT;
