CREATE TABLE IF NOT EXISTS auth_svc.password_reset_otps (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    otp VARCHAR(6) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_password_reset_otps_email_otp ON auth_svc.password_reset_otps(email, otp);
CREATE INDEX IF NOT EXISTS idx_password_reset_otps_expires_at ON auth_svc.password_reset_otps(expires_at);