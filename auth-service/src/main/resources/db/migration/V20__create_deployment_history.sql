-- Create deployment history table to track all deployments per candidate
CREATE TABLE IF NOT EXISTS auth_svc.deployment_history (
    id VARCHAR(36) PRIMARY KEY,
    candidate_id VARCHAR(36) NOT NULL,
    emp_id VARCHAR(50),
    client_name VARCHAR(255) NOT NULL,
    deployed_date DATE NOT NULL,
    end_date DATE,
    mentor VARCHAR(255),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_deployment_candidate FOREIGN KEY (candidate_id)
        REFERENCES auth_svc.users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_deployment_candidate ON auth_svc.deployment_history(candidate_id);
CREATE INDEX IF NOT EXISTS idx_deployment_status ON auth_svc.deployment_history(status);
CREATE INDEX IF NOT EXISTS idx_deployment_end_date ON auth_svc.deployment_history(end_date);