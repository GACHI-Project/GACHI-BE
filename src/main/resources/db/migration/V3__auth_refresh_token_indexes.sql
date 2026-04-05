CREATE INDEX IF NOT EXISTS idx_auth_refresh_tokens_jti_token_hash
    ON auth_refresh_tokens (jti, token_hash);

CREATE INDEX IF NOT EXISTS idx_auth_refresh_tokens_revoked_updated_at
    ON auth_refresh_tokens (revoked_at, updated_at);