-- V2 was edited to add `institute` and widen `branch` to VARCHAR(64) after it had already been
-- applied to some databases (Flyway records migrations as immutable once run; validate-on-migrate
-- is off here, so the mismatch went undetected instead of failing loudly on startup). This forward
-- migration brings already-migrated databases in line with the current V2 file/entity; it's a no-op
-- on databases that ran the current V2 content from scratch.
ALTER TABLE screening_svc.screening_candidates
    ADD COLUMN IF NOT EXISTS institute VARCHAR(32);

ALTER TABLE screening_svc.screening_candidates
    ALTER COLUMN branch TYPE VARCHAR(64);
