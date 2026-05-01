-- Update existing NOT_RFD values to WFD
-- This runs after V15 which added the new enum values

UPDATE auth_svc.users 
SET candidate_status = 'WFD' 
WHERE candidate_status = 'NOT_RFD';

-- Note: PostgreSQL doesn't support removing enum values directly
-- The NOT_RFD value will remain in the enum type but won't be used
-- New inserts/updates should only use RFD, WFD, or DOB
