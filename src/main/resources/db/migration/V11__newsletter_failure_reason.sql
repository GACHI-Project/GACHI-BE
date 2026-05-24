ALTER TABLE newsletter
    ADD COLUMN IF NOT EXISTS failure_stage VARCHAR(50) NULL;

ALTER TABLE newsletter
    ADD COLUMN IF NOT EXISTS failure_reason TEXT NULL;
