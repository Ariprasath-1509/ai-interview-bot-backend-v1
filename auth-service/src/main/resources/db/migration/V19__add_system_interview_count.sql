-- Add system_interview_count column to track interviews taken in our application
-- This is separate from no_of_interviews which tracks external client interviews

ALTER TABLE auth_svc.users
ADD COLUMN system_interview_count INTEGER DEFAULT 0 NOT NULL;

-- Create index for efficient filtering
CREATE INDEX idx_users_system_interview_count ON auth_svc.users(system_interview_count);

-- Backfill historical counts when interview tables already exist (upgrade path).
-- On a fresh test-server deploy, interview-service has not migrated yet — skip safely.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'interview_svc'
          AND table_name = 'interviews'
    ) THEN
        UPDATE auth_svc.users u
        SET system_interview_count = (
            SELECT COUNT(DISTINCT i.id)
            FROM interview_svc.interviews i
            JOIN interview_svc.engineers e ON i.engineer_id = e.id
            WHERE (e.email = u.email OR e.email = u.official_email OR e.email = u.personal_email)
              AND i.status IN ('COMPLETED', 'SIGNED_OFF')
        )
        WHERE u.role = 'CANDIDATE';
    END IF;
END $$;

COMMENT ON COLUMN auth_svc.users.system_interview_count IS 'Auto-incremented count of interviews taken in our application (COMPLETED or SIGNED_OFF status)';