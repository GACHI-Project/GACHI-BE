ALTER TABLE notification_delivery_logs
    ADD COLUMN IF NOT EXISTS provider VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN';

CREATE INDEX IF NOT EXISTS idx_notification_delivery_logs_provider
    ON notification_delivery_logs (provider, attempted_at DESC);
