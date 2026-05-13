UPDATE newsletter
SET is_saved = TRUE
WHERE is_saved IS DISTINCT FROM TRUE;

ALTER TABLE newsletter
    ALTER COLUMN is_saved SET DEFAULT TRUE;

DROP INDEX IF EXISTS idx_newsletter_user_saved;
