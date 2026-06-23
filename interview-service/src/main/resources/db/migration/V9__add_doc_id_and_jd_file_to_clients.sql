-- V9__add_doc_id_and_jd_file_to_clients.sql
ALTER TABLE interview_svc.clients ADD COLUMN doc_id VARCHAR(255);
ALTER TABLE interview_svc.clients ADD COLUMN jd_file BYTEA;
ALTER TABLE interview_svc.clients ADD COLUMN jd_file_name VARCHAR(255);