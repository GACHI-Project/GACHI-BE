ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS template_key VARCHAR(80),
    ADD COLUMN IF NOT EXISTS template_params_json TEXT;

ALTER TABLE newsletter
    ADD COLUMN IF NOT EXISTS title_i18n JSONB;

ALTER TABLE calendar_events
    ADD COLUMN IF NOT EXISTS title_i18n JSONB;

ALTER TABLE checklist
    ADD COLUMN IF NOT EXISTS content_i18n JSONB;

CREATE INDEX IF NOT EXISTS idx_notifications_template_key
    ON notifications (template_key)
    WHERE template_key IS NOT NULL;
