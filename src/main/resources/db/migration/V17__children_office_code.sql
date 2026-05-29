ALTER TABLE children
    ADD COLUMN IF NOT EXISTS office_code VARCHAR(20);

CREATE INDEX IF NOT EXISTS idx_children_user_school_identity
    ON children (user_id, office_code, school_code)
    WHERE deleted_at IS NULL;
