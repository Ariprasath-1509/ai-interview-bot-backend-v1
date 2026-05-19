-- Create LLM configuration table
CREATE TABLE IF NOT EXISTS llm_configuration (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(20) NOT NULL,
    
    -- Claude models
    claude_question_model VARCHAR(100),
    claude_assessment_model VARCHAR(100),
    claude_rubric_model VARCHAR(100),
    claude_matching_model VARCHAR(100),
    
    -- Ollama models
    ollama_question_model VARCHAR(100),
    ollama_assessment_model VARCHAR(100),
    ollama_rubric_model VARCHAR(100),
    ollama_matching_model VARCHAR(100),
    
    updated_by BIGINT NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_provider CHECK (provider IN ('CLAUDE', 'OLLAMA'))
);

-- Insert default configuration only if table is empty
INSERT INTO llm_configuration (
    id, provider,
    claude_question_model, claude_assessment_model, claude_rubric_model, claude_matching_model,
    ollama_question_model, ollama_assessment_model, ollama_rubric_model, ollama_matching_model,
    updated_by
)
SELECT 
    1, 'CLAUDE',
    'claude-haiku-4-5', 'claude-sonnet-4-5', 'claude-haiku-4-5', 'claude-sonnet-4-5',
    'qwen2.5:7b', 'qwen2.5:32b', 'qwen2.5:14b', 'qwen2.5:32b',
    1
WHERE NOT EXISTS (SELECT 1 FROM llm_configuration WHERE id = 1);
