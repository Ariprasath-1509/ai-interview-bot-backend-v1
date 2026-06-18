-- Re-sync staff branch after role changes (testing roles must store TESTING branch)

UPDATE auth_svc.users
SET branch = 'TESTING'
WHERE role IN ('TESTING_ADMIN', 'TESTING_RECRUITER')
  AND branch IS DISTINCT FROM 'TESTING';

UPDATE auth_svc.users
SET branch = 'DEVELOPMENT'
WHERE role IN ('ADMIN', 'RECRUITER', 'SUPER_ADMIN')
  AND branch IS DISTINCT FROM 'DEVELOPMENT';
