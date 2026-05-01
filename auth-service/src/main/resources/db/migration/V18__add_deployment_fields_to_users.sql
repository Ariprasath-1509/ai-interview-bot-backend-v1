-- Add deployment fields to users table
ALTER TABLE auth_svc.users ADD COLUMN IF NOT EXISTS emp_id VARCHAR(50) UNIQUE;
ALTER TABLE auth_svc.users ADD COLUMN IF NOT EXISTS deployed_client_name VARCHAR(255);
ALTER TABLE auth_svc.users ADD COLUMN IF NOT EXISTS deployed_date DATE;
ALTER TABLE auth_svc.users ADD COLUMN IF NOT EXISTS mentor VARCHAR(255);

-- Create index for faster deployed candidates queries
CREATE INDEX IF NOT EXISTS idx_users_deployed_status ON auth_svc.users(candidate_status) WHERE candidate_status = 'DEPLOYED';
CREATE INDEX IF NOT EXISTS idx_users_emp_id ON auth_svc.users(emp_id) WHERE emp_id IS NOT NULL;
