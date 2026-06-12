ALTER TABLE newsletter
    ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64) NULL;

CREATE INDEX IF NOT EXISTS idx_newsletter_child_content_hash
    ON newsletter (user_id, child_name, content_hash)
    WHERE child_name IS NOT NULL
      AND content_hash IS NOT NULL
      AND status IN ('PENDING', 'PROCESSING', 'COMPLETED');

CREATE INDEX IF NOT EXISTS idx_newsletter_no_child_content_hash
    ON newsletter (user_id, content_hash)
    WHERE child_name IS NULL
      AND content_hash IS NOT NULL
      AND status IN ('PENDING', 'PROCESSING', 'COMPLETED');
