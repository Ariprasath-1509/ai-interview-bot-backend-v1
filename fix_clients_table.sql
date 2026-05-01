-- Manual fix for clients table
-- Run this directly in PostgreSQL

-- Connect to your database first
-- \c bench_readiness

-- Drop existing table if it exists
DROP TABLE IF EXISTS interview_svc.clients CASCADE;

-- Create the correct table structure
CREATE TABLE interview_svc.clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_name VARCHAR(255) NOT NULL,
    jd_role VARCHAR(255) NOT NULL,
    jd_description TEXT NOT NULL,
    positions_vacant INTEGER NOT NULL,
    market_candidates_needed INTEGER NOT NULL,
    bench_b2b_candidates_needed INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    bench_reviewed BOOLEAN NOT NULL DEFAULT FALSE,
    recruitment_reviewed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX idx_clients_bench_pending ON interview_svc.clients (bench_reviewed, bench_b2b_candidates_needed);
CREATE INDEX idx_clients_recruitment_pending ON interview_svc.clients (recruitment_reviewed, market_candidates_needed);
CREATE INDEX idx_clients_created_at ON interview_svc.clients (created_at);

-- Insert sample data
INSERT INTO interview_svc.clients (client_name, jd_role, jd_description, positions_vacant, market_candidates_needed, bench_b2b_candidates_needed, status) VALUES 
('TechCorp Solutions', 'Senior Java Developer', 'Looking for experienced Java developer with Spring Boot expertise. Must have 5+ years experience in enterprise applications, microservices architecture, and REST API development.', 3, 1, 2, 'ACTIVE'),
('InnovateLabs', 'Full Stack Engineer', 'React + Node.js developer for modern web applications. Experience with cloud platforms preferred. Should have strong problem-solving skills and ability to work in agile environment.', 2, 1, 1, 'ACTIVE'),
('DataDriven Inc', 'DevOps Engineer', 'Kubernetes, Docker, CI/CD pipeline expertise required. AWS/Azure experience mandatory. Looking for someone who can automate deployment processes and manage cloud infrastructure.', 1, 0, 1, 'ACTIVE'),
('CloudFirst Technologies', 'Backend Architect', 'Microservices architecture design and implementation. Leadership experience required. Should be able to mentor junior developers and make technical decisions for large-scale systems.', 2, 1, 1, 'ACTIVE');

-- Verify the table structure
\d interview_svc.clients;

-- Check the data
SELECT * FROM interview_svc.clients;