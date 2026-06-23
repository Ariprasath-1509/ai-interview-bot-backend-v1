-- V10: Add candidate matching cache table
CREATE TABLE interview_svc.candidate_matching_cache (
    id VARCHAR(36) PRIMARY KEY,
    candidate_id VARCHAR(36) NOT NULL,
    candidate_email VARCHAR(255) NOT NULL,
    matching_results_json TEXT,
    total_clients_analyzed INTEGER,
    matching_clients_count INTEGER,
    average_match_score DECIMAL(3,2),
    computed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,
    cache_source VARCHAR(20),
    
    CONSTRAINT fk_candidate_matching_cache_candidate_id 
        FOREIGN KEY (candidate_id) REFERENCES auth_svc.users(id) ON DELETE CASCADE
);

-- Create indexes for performance
CREATE INDEX idx_candidate_matching_cache_candidate_id ON interview_svc.candidate_matching_cache(candidate_id);
CREATE INDEX idx_candidate_matching_cache_email ON interview_svc.candidate_matching_cache(candidate_email);
CREATE INDEX idx_candidate_matching_cache_expires_at ON interview_svc.candidate_matching_cache(expires_at);
CREATE INDEX idx_candidate_matching_cache_computed_at ON interview_svc.candidate_matching_cache(computed_at);