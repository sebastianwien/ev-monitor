CREATE TYPE fixed_cost_category AS ENUM (
    'INSURANCE',
    'TAX',
    'TOLL',
    'CLEANING',
    'MAINTENANCE',
    'OTHER'
);

CREATE TYPE fixed_cost_recurrence AS ENUM (
    'ONE_TIME',
    'MONTHLY',
    'QUARTERLY',
    'YEARLY'
);

CREATE TABLE fixed_cost (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    car_id      UUID NOT NULL REFERENCES car(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    description VARCHAR(255) NOT NULL,
    amount      NUMERIC(10, 2) NOT NULL CHECK (amount >= 0),
    category    fixed_cost_category NOT NULL,
    recurrence  fixed_cost_recurrence NOT NULL,
    date        DATE,
    start_date  DATE,
    end_date    DATE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fixed_cost_one_time_has_date
        CHECK (recurrence != 'ONE_TIME' OR date IS NOT NULL),
    CONSTRAINT fixed_cost_recurring_has_start
        CHECK (recurrence = 'ONE_TIME' OR start_date IS NOT NULL)
);

CREATE INDEX idx_fixed_cost_car_id ON fixed_cost(car_id);
CREATE INDEX idx_fixed_cost_user_id ON fixed_cost(user_id);
