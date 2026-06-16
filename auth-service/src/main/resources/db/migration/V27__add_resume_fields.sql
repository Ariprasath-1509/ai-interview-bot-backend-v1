-- Resume storage fields on candidate users (used by bulk import and profile upload)

ALTER TABLE auth_svc.users ADD COLUMN IF NOT EXISTS resume_filename VARCHAR(255);
ALTER TABLE auth_svc.users ADD COLUMN IF NOT EXISTS resume_file_path VARCHAR(500);
ALTER TABLE auth_svc.users ADD COLUMN IF NOT EXISTS resume_parsed_text TEXT;
ALTER TABLE auth_svc.users ADD COLUMN IF NOT EXISTS resume_summary TEXT;
ALTER TABLE auth_svc.users ADD COLUMN IF NOT EXISTS resume_uploaded_at TIMESTAMP;
ALTER TABLE auth_svc.users ADD COLUMN IF NOT EXISTS resume_updated_at TIMESTAMP;
