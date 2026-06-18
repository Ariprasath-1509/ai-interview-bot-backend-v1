-- Align stored branch with role for testing staff (existing dev staff already default to DEVELOPMENT)

UPDATE auth_svc.users
SET branch = 'TESTING'
WHERE role IN ('TESTING_ADMIN', 'TESTING_RECRUITER')
  AND branch <> 'TESTING';
