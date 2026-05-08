-- Add sample audit logs for testing the compliance dashboard

INSERT INTO compliance_svc.audit_logs (id, actor_id, actor_role, action, resource, resource_id, detail, ip_address, created_at)
VALUES 
(gen_random_uuid()::text, 'admin@benchreadiness.com', 'SUPER_ADMIN', 'CREATE_INTERVIEW', 'INTERVIEW', gen_random_uuid()::text, 'Created interview for Java Developer role', '127.0.0.1', NOW() - INTERVAL '1 day'),
(gen_random_uuid()::text, 'admin@benchreadiness.com', 'SUPER_ADMIN', 'VIEW_TRANSCRIPT', 'INTERVIEW', gen_random_uuid()::text, 'Viewed interview transcript', '127.0.0.1', NOW() - INTERVAL '2 hours'),
(gen_random_uuid()::text, 'recruiter@benchreadiness.com', 'RECRUITER', 'ACCESS_SCORES', 'INTERVIEW', gen_random_uuid()::text, 'Accessed candidate scores', '192.168.1.100', NOW() - INTERVAL '3 hours'),
(gen_random_uuid()::text, 'admin@benchreadiness.com', 'ADMIN', 'UPDATE_CANDIDATE', 'CANDIDATE', gen_random_uuid()::text, 'Updated candidate rating to ASSET', '127.0.0.1', NOW() - INTERVAL '5 hours'),
(gen_random_uuid()::text, 'admin@benchreadiness.com', 'SUPER_ADMIN', 'CREATE_CLIENT', 'CLIENT', gen_random_uuid()::text, 'Created new client TechCorp', '127.0.0.1', NOW() - INTERVAL '1 day'),
(gen_random_uuid()::text, 'recruiter@benchreadiness.com', 'RECRUITER', 'VIEW_INTERVIEW', 'INTERVIEW', gen_random_uuid()::text, 'Viewed interview details', '192.168.1.100', NOW() - INTERVAL '6 hours'),
(gen_random_uuid()::text, 'admin@benchreadiness.com', 'ADMIN', 'SIGN_OFF_INTERVIEW', 'INTERVIEW', gen_random_uuid()::text, 'Signed off interview with verdict READY', '127.0.0.1', NOW() - INTERVAL '8 hours'),
(gen_random_uuid()::text, 'admin@benchreadiness.com', 'SUPER_ADMIN', 'DELETE_STAFF', 'USER', gen_random_uuid()::text, 'Deleted staff account', '127.0.0.1', NOW() - INTERVAL '2 days'),
(gen_random_uuid()::text, 'recruiter@benchreadiness.com', 'RECRUITER', 'ACCESS_MATCHING', 'CLIENT', gen_random_uuid()::text, 'Accessed AI matching dashboard', '192.168.1.100', NOW() - INTERVAL '4 hours'),
(gen_random_uuid()::text, 'admin@benchreadiness.com', 'ADMIN', 'UPDATE_TOKEN_LIMIT', 'SYSTEM', 'token-settings', 'Updated daily token limit to 150000', '127.0.0.1', NOW() - INTERVAL '3 days');