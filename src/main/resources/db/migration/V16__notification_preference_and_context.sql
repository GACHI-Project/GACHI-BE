ALTER TABLE users
    ADD COLUMN IF NOT EXISTS notification_preference VARCHAR(20);

UPDATE users
SET notification_preference = CASE
    WHEN notification_enabled = FALSE THEN 'OFF'
    ELSE 'IMPORTANT'
END
WHERE notification_preference IS NULL;

ALTER TABLE users
    ALTER COLUMN notification_preference SET DEFAULT 'IMPORTANT',
    ALTER COLUMN notification_preference SET NOT NULL;

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS chk_users_notification_preference;

ALTER TABLE users
    ADD CONSTRAINT chk_users_notification_preference
        CHECK (notification_preference IN ('URGENT_ONLY', 'IMPORTANT', 'ALL', 'OFF'));

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS level VARCHAR(20),
    ADD COLUMN IF NOT EXISTS child_id BIGINT,
    ADD COLUMN IF NOT EXISTS child_name VARCHAR(50);

UPDATE notifications
SET level = 'IMPORTANT'
WHERE level IS NULL;

ALTER TABLE notifications
    ALTER COLUMN level SET DEFAULT 'IMPORTANT',
    ALTER COLUMN level SET NOT NULL;

ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS chk_notifications_type;

ALTER TABLE notifications
    ADD CONSTRAINT chk_notifications_type
        CHECK (type IN (
            'NEWSLETTER_ANALYSIS',
            'DEADLINE_REMINDER',
            'CALENDAR_EVENT',
            'CHECKLIST_DUE',
            'WEEKLY_SUMMARY',
            'SYSTEM',
            'ANNOUNCEMENT'
        ));

ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS chk_notifications_level;

ALTER TABLE notifications
    ADD CONSTRAINT chk_notifications_level
        CHECK (level IN ('URGENT', 'IMPORTANT', 'NORMAL'));

CREATE INDEX IF NOT EXISTS idx_notifications_user_child_id
    ON notifications (user_id, child_id, id DESC)
    WHERE child_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_notifications_user_child_name
    ON notifications (user_id, child_name, id DESC)
    WHERE child_name IS NOT NULL;
