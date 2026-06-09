ALTER TABLE children
    ADD COLUMN IF NOT EXISTS class_name VARCHAR(20);

CREATE INDEX IF NOT EXISTS idx_children_user_school_class
    ON children (user_id, office_code, school_code, grade, class_name)
    WHERE deleted_at IS NULL;
