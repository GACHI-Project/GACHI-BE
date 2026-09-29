ALTER TABLE calendar_events
    ADD COLUMN period_start_at VARCHAR(35) NULL;

ALTER TABLE calendar_events
    ADD COLUMN all_day BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE calendar_events
    ADD COLUMN end_all_day BOOLEAN NOT NULL DEFAULT FALSE;

-- Legacy rows have no date-only marker. KST midnight is the closest recoverable proxy.
UPDATE calendar_events
SET all_day = ((start_at AT TIME ZONE 'Asia/Seoul')::time = TIME '00:00'),
    end_all_day = (
        end_at IS NOT NULL
        AND (end_at AT TIME ZONE 'Asia/Seoul')::time = TIME '00:00'
    );
