CREATE TABLE anonymous_browser_keys (
    key_hash VARCHAR(64) PRIMARY KEY,
    user_token VARCHAR(64) NOT NULL REFERENCES chat_users(token),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_anonymous_browser_keys_user ON anonymous_browser_keys(user_token);
