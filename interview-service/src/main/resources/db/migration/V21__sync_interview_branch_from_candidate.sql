-- Align interview branch with the linked candidate (auth user) via engineer record

UPDATE interview_svc.interviews i
SET branch = u.branch
FROM interview_svc.engineers e
JOIN auth_svc.users u ON u.id = e.user_id
WHERE i.engineer_id = e.id
  AND u.role = 'CANDIDATE'
  AND u.branch IS NOT NULL
  AND i.branch IS DISTINCT FROM u.branch;

UPDATE interview_svc.interviews i
SET branch = u.branch
FROM interview_svc.engineers e
JOIN auth_svc.users u ON LOWER(u.email) = LOWER(e.email)
WHERE i.engineer_id = e.id
  AND u.role = 'CANDIDATE'
  AND u.branch IS NOT NULL
  AND i.branch IS DISTINCT FROM u.branch;

-- Fallback: derive branch from linked client when interview branch still mismatches
UPDATE interview_svc.interviews i
SET branch = c.branch
FROM interview_svc.clients c
WHERE i.client_id = c.id
  AND c.branch IS NOT NULL
  AND i.branch IS DISTINCT FROM c.branch;
