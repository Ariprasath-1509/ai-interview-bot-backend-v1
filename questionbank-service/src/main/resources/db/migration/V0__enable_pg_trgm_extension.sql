-- Pre-deployment check: Enable pg_trgm extension
-- Run this on the remote database (103.182.211.219:5434) BEFORE deploying questionbank-service
-- This ensures Flyway migration won't fail on first startup

CREATE EXTENSION IF NOT EXISTS pg_trgm;