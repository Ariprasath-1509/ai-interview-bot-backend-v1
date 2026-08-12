-- Global toggle for strict interview lockdown (immediate termination on tab-switch /
-- window-blur / fullscreen-exit, plus best-effort OS key capture). Admin-configurable,
-- defaults to enabled.

INSERT INTO master_data_svc.entries (category, code, label, display_order, metadata)
VALUES ('SYSTEM_SETTING', 'STRICT_LOCKDOWN', 'Strict Lockdown Mode', 1, '{"enabled": true}')
ON CONFLICT (category, code) DO NOTHING;
