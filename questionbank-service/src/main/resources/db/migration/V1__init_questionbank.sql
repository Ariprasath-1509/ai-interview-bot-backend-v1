-- questionbank-service schema initialization

-- Enable pg_trgm extension for fuzzy matching (requires superuser or extension already available)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE SCHEMA IF NOT EXISTS questionbank_svc;

-- Categories table
CREATE TABLE questionbank_svc.categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    interview_type VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Companies table
CREATE TABLE questionbank_svc.companies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    slug VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Tags table
CREATE TABLE questionbank_svc.tags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE
);

-- Questions table
CREATE TABLE questionbank_svc.questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    text TEXT NOT NULL,
    category_id UUID NOT NULL REFERENCES questionbank_svc.categories(id),
    occurrence_count INTEGER DEFAULT 0,
    relevancy_score DOUBLE PRECISION DEFAULT 0.0,
    relevancy_label VARCHAR(50) DEFAULT 'LOW',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Question-Tags join table
CREATE TABLE questionbank_svc.question_tags (
    question_id UUID REFERENCES questionbank_svc.questions(id) ON DELETE CASCADE,
    tag_id UUID REFERENCES questionbank_svc.tags(id) ON DELETE CASCADE,
    PRIMARY KEY (question_id, tag_id)
);

-- Interview Sessions table
CREATE TABLE questionbank_svc.interview_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    candidate_name VARCHAR(255) NOT NULL,
    company_id UUID NOT NULL REFERENCES questionbank_svc.companies(id),
    round VARCHAR(255) NOT NULL,
    interview_date DATE,
    interviewer_name VARCHAR(255),
    candidate_id UUID,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Question Occurrences table (tracks which questions were used in sessions)
CREATE TABLE questionbank_svc.question_occurrences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id UUID NOT NULL REFERENCES questionbank_svc.questions(id) ON DELETE CASCADE,
    session_id UUID NOT NULL REFERENCES questionbank_svc.interview_sessions(id) ON DELETE CASCADE
);

-- Email Logs table
CREATE TABLE questionbank_svc.email_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sent_by UUID,
    subject VARCHAR(255) NOT NULL,
    recipient_count INTEGER NOT NULL,
    recipient_emails TEXT[],
    filters JSONB,
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_questions_category ON questionbank_svc.questions(category_id);
CREATE INDEX idx_questions_relevancy ON questionbank_svc.questions(relevancy_score);
CREATE INDEX idx_questions_relevancy_label ON questionbank_svc.questions(relevancy_label);
CREATE INDEX idx_questions_text_trgm ON questionbank_svc.questions USING gin (text gin_trgm_ops);
CREATE INDEX idx_sessions_company ON questionbank_svc.interview_sessions(company_id);
CREATE INDEX idx_sessions_date ON questionbank_svc.interview_sessions(interview_date);
CREATE INDEX idx_sessions_candidate ON questionbank_svc.interview_sessions(candidate_id);
CREATE INDEX idx_occurrences_question ON questionbank_svc.question_occurrences(question_id);
CREATE INDEX idx_occurrences_session ON questionbank_svc.question_occurrences(session_id);
CREATE INDEX idx_email_logs_sent_by ON questionbank_svc.email_logs(sent_by);
CREATE INDEX idx_email_logs_sent_at ON questionbank_svc.email_logs(sent_at);

-- Seed predefined categories
INSERT INTO questionbank_svc.categories (id, name, interview_type) VALUES
    (gen_random_uuid(), 'Java', 'backend'),
    (gen_random_uuid(), 'Spring', 'backend'),
    (gen_random_uuid(), 'Microservices', 'backend'),
    (gen_random_uuid(), 'System Design', 'backend'),
    (gen_random_uuid(), 'Database', 'backend'),
    (gen_random_uuid(), 'Messaging', 'backend'),
    (gen_random_uuid(), 'DevOps', 'backend'),
    (gen_random_uuid(), 'Security', 'backend'),
    (gen_random_uuid(), 'JavaScript', 'frontend'),
    (gen_random_uuid(), 'TypeScript', 'frontend'),
    (gen_random_uuid(), 'React', 'frontend'),
    (gen_random_uuid(), 'Angular', 'frontend'),
    (gen_random_uuid(), 'Node', 'frontend'),
    (gen_random_uuid(), 'Web Fundamentals', 'frontend'),
    (gen_random_uuid(), 'DSA', 'shared'),
    (gen_random_uuid(), 'Design Patterns', 'shared'),
    (gen_random_uuid(), 'General', 'shared')
ON CONFLICT (name) DO NOTHING;