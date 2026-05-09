UPDATE newsletter
SET is_saved = TRUE
WHERE is_saved = FALSE;

ALTER TABLE newsletter
    ALTER COLUMN is_saved SET DEFAULT TRUE;

DROP INDEX IF EXISTS idx_newsletter_user_saved;
