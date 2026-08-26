CREATE TABLE meal_days (
    meal_date DATE PRIMARY KEY,
    restaurant_name VARCHAR(255) NOT NULL,
    complete BOOLEAN NOT NULL,
    meals_json TEXT NOT NULL,
    message TEXT,
    last_updated_at TIMESTAMPTZ
);

CREATE TABLE rating_votes (
    id BIGSERIAL PRIMARY KEY,
    meal_date DATE NOT NULL,
    meal_id VARCHAR(64) NOT NULL,
    client_id VARCHAR(128) NOT NULL,
    stars INTEGER NOT NULL CHECK (stars BETWEEN 1 AND 5),
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_rating_vote UNIQUE (meal_date, meal_id, client_id)
);

CREATE INDEX idx_rating_votes_date ON rating_votes (meal_date);

CREATE TABLE visitor_visits (
    id BIGSERIAL PRIMARY KEY,
    visit_date DATE NOT NULL,
    client_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_visitor_visit UNIQUE (visit_date, client_id)
);

CREATE INDEX idx_visitor_visits_date ON visitor_visits (visit_date);
