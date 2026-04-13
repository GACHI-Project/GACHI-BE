CREATE TABLE checklist_items (
                                 id            BIGSERIAL PRIMARY KEY,
                                 newsletter_id BIGINT       NOT NULL,
                                 content       VARCHAR(500) NOT NULL,
                                 detail        VARCHAR(500),
                                 is_checked    BOOLEAN      NOT NULL DEFAULT FALSE,
                                 created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_checklist_items_newsletter_id ON checklist_items (newsletter_id);

CREATE TABLE todo_items (
                            id                 BIGSERIAL PRIMARY KEY,
                            newsletter_id      BIGINT       NOT NULL,
                            content            VARCHAR(500) NOT NULL,
                            target_date        DATE,
                            target_date_label  VARCHAR(50),
                            created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_todo_items_newsletter_id ON todo_items (newsletter_id);
CREATE INDEX idx_todo_items_target_date   ON todo_items (target_date) WHERE target_date IS NOT NULL;
