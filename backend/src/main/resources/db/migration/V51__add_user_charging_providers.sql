-- Stores the charging provider (EMP) history per user.
-- Replaces the single primary_emp field in favor of a full history
-- to enable historical tariff analysis and accurate comparison calculations.
CREATE TABLE user_charging_providers (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    provider_name    VARCHAR(100) NOT NULL,
    ac_price_per_kwh NUMERIC(6,4)     NULL,
    dc_price_per_kwh NUMERIC(6,4)     NULL,
    monthly_fee_eur  NUMERIC(8,2)     NOT NULL DEFAULT 0,
    session_fee_eur  NUMERIC(6,4)     NOT NULL DEFAULT 0,
    active_from      DATE             NOT NULL,
    active_until     DATE             NULL,  -- NULL = currently active
    created_at       TIMESTAMP        NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_charging_providers_user_id ON user_charging_providers(user_id);
CREATE INDEX idx_user_charging_providers_active ON user_charging_providers(user_id, active_until) WHERE active_until IS NULL;
