-- Add new skill set values to the enum
ALTER TYPE auth_svc.skill_set ADD VALUE IF NOT EXISTS 'ANGULAR';
ALTER TYPE auth_svc.skill_set ADD VALUE IF NOT EXISTS 'PYTHON';
ALTER TYPE auth_svc.skill_set ADD VALUE IF NOT EXISTS 'QA_ENGINEER';
ALTER TYPE auth_svc.skill_set ADD VALUE IF NOT EXISTS 'PLAYWRIGHT_AUTOMATION';