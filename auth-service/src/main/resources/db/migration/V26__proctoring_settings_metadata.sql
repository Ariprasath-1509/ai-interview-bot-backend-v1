-- Admin-configurable video proctoring per candidate source (stored in master data metadata)

UPDATE master_data_svc.entries
SET metadata = COALESCE(metadata, '{}'::jsonb) || '{"videoProctoringEnabled": false}'::jsonb,
    updated_at = NOW()
WHERE category = 'CANDIDATE_SOURCE'
  AND code IN ('BENCH', 'B2B');

UPDATE master_data_svc.entries
SET metadata = COALESCE(metadata, '{}'::jsonb) || '{"videoProctoringEnabled": true}'::jsonb,
    updated_at = NOW()
WHERE category = 'CANDIDATE_SOURCE'
  AND code = 'MARKET';
