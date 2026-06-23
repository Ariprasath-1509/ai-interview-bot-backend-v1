-- TRAINING imports often omit source; treat unset source as bench pool for BENCH/B2B admins

UPDATE auth_svc.users
SET source = 'BENCH'
WHERE role = 'CANDIDATE'
  AND source IS NULL
  AND branch = 'DEVELOPMENT';
