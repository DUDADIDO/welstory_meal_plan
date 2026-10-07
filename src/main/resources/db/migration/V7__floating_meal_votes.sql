CREATE TABLE meal_choices (
    meal_date DATE NOT NULL,
    user_token VARCHAR(64) NOT NULL REFERENCES chat_users(token),
    meal_id VARCHAR(64) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (meal_date, user_token)
);
CREATE INDEX idx_meal_choices_counts ON meal_choices(meal_date, meal_id);

CREATE TABLE meal_choice_limits (
    user_token VARCHAR(64) PRIMARY KEY REFERENCES chat_users(token),
    quota_date DATE NOT NULL,
    daily_count INTEGER NOT NULL CHECK (daily_count BETWEEN 0 AND 10),
    last_vote_at TIMESTAMPTZ NOT NULL
);
