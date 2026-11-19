ALTER TABLE kakao_unlink_outbox
    ADD COLUMN event_type VARCHAR(30) NOT NULL DEFAULT 'UNLINK_REQUEST';

ALTER TABLE kakao_unlink_outbox
    ADD CONSTRAINT chk_kakao_unlink_outbox_event_type
        CHECK (event_type IN ('UNLINK_REQUEST', 'WEBHOOK_VERIFY'));
