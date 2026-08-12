ALTER TABLE interview_svc.clients ADD COLUMN IF NOT EXISTS screening_checklist_json TEXT;
ALTER TABLE interview_svc.clients ADD COLUMN IF NOT EXISTS screening_checklist_name VARCHAR(255);
