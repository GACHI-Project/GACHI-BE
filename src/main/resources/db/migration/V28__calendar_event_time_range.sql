ALTER TABLE calendar_events
    ADD COLUMN period_start_at VARCHAR(35) NULL;

ALTER TABLE calendar_events
    ADD COLUMN all_day BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE calendar_events
    ADD COLUMN end_all_day BOOLEAN NOT NULL DEFAULT FALSE;
