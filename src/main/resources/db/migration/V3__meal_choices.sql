CREATE TABLE meal_choices (
    meal_date DATE NOT NULL,
    user_token VARCHAR(64) NOT NULL REFERENCES chat_users(token),
    meal_id VARCHAR(64) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (meal_date, user_token)
);

CREATE INDEX idx_meal_choices_counts ON meal_choices (meal_date, meal_id);
