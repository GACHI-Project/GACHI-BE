ALTER TABLE newsletter
    ADD COLUMN IF NOT EXISTS date_candidates JSONB NULL;
