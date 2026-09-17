CREATE TABLE app_user_api_keys (
    id UUID PRIMARY KEY,
    app_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE ON UPDATE CASCADE,
    name VARCHAR(150) NOT NULL,
    key_prefix VARCHAR(16) NOT NULL,
    hashed_key VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_app_user_api_keys_hashed_key UNIQUE (hashed_key)
);

CREATE INDEX idx_app_user_api_keys_app_user_id ON app_user_api_keys (app_user_id);
