CREATE TABLE chat_users (
    token VARCHAR(64) PRIMARY KEY,
    nickname VARCHAR(80) NOT NULL UNIQUE,
    quota_date DATE NOT NULL,
    daily_count INTEGER NOT NULL DEFAULT 0 CHECK (daily_count BETWEEN 0 AND 50),
    last_message_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE chat_messages (
    id BIGSERIAL PRIMARY KEY,
    meal_date DATE NOT NULL,
    meal_id VARCHAR(64) NOT NULL,
    user_token VARCHAR(64) NOT NULL REFERENCES chat_users(token),
    content VARCHAR(500) NOT NULL CHECK (char_length(content) BETWEEN 1 AND 500),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_chat_messages_room ON chat_messages (meal_date, meal_id, id DESC);
