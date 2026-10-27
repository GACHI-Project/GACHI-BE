ALTER TABLE social_accounts
    DROP CONSTRAINT IF EXISTS chk_social_accounts_provider;

ALTER TABLE social_accounts
    ADD CONSTRAINT chk_social_accounts_provider
        CHECK (provider IN ('KAKAO', 'GOOGLE'));
