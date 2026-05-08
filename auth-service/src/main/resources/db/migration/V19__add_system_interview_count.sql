-- Add system_interview_count column to track interviews taken in our application
-- This is separate from no_of_interviews which tracks external client interviews

ALTER TABLE auth_svc.users
ADD COLUMN system_interview_count INTEGER DEFAULT 0 NOT NULL;

-- Create index for efficient filtering
CREATE INDEX idx_users_system_interview_count ON auth_svc.users(system_interview_count);

-- Update existing users with count from interview_svc.interviews
-- This is a one-time migration to populate historical data
UPDATE auth_svc.users u
SET system_interview_count = (
    SELECT COUNT(DISTINCT i.id)
    FROM interview_svc.interviews i
    JOIN interview_svc.engineers e ON i.engineer_id = e.id
    WHERE (e.email = u.email OR e.email = u.official_email OR e.email = u.personal_email)
    AND i.status IN ('COMPLETED', 'SIGNED_OFF')
)
WHERE u.role = 'CANDIDATE';

COMMENT ON COLUMN auth_svc.users.system_interview_count IS 'Auto-incremented count of interviews taken in our application (COMPLETED or SIGNED_OFF status)';