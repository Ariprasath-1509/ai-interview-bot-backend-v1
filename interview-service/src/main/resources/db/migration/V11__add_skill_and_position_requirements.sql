-- Add skill requirements and position requirements tables
CREATE TABLE interview_svc.skill_requirements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID NOT NULL,
    skill_set VARCHAR(50) NOT NULL,
    FOREIGN KEY (client_id) REFERENCES interview_svc.clients(id) ON DELETE CASCADE
);

CREATE TABLE interview_svc.position_requirements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    skill_requirement_id UUID NOT NULL,
    candidates_needed INTEGER NOT NULL,
    min_yoe_required DECIMAL(3,1) NOT NULL,
    source VARCHAR(20) NOT NULL,
    FOREIGN KEY (skill_requirement_id) REFERENCES interview_svc.skill_requirements(id) ON DELETE CASCADE
);

-- Create indexes for better performance
CREATE INDEX idx_skill_requirements_client_id ON interview_svc.skill_requirements(client_id);
CREATE INDEX idx_position_requirements_skill_requirement_id ON interview_svc.position_requirements(skill_requirement_id);
CREATE INDEX idx_position_requirements_source ON interview_svc.position_requirements(source);
CREATE INDEX idx_position_requirements_min_yoe ON interview_svc.position_requirements(min_yoe_required);