-- F4: persistent rubric cache so the in-memory Spring cache doesn't re-generate on every pod restart
CREATE TABLE compliance_svc.rubric_cache (
    id          VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::text,
    cache_key   VARCHAR(64)  NOT NULL UNIQUE,
    rubric_json TEXT         NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rubric_cache_key ON compliance_svc.rubric_cache(cache_key);

-- F3: raise daily limit from 100,000 to 500,000 (one full L3 interview costs ~75-92k tokens)
UPDATE compliance_svc.daily_token_limits
SET    daily_limit       = 500000,
       warning_threshold = 400000,
       updated_at        = NOW()
WHERE  organization_id   = 'default';
