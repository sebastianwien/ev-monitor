ALTER TABLE app_user
    ADD COLUMN referral_code VARCHAR(8) NOT NULL DEFAULT upper(substring(gen_random_uuid()::text, 1, 8)),
    ADD COLUMN referred_by_user_id UUID REFERENCES app_user(id) ON DELETE SET NULL;

CREATE UNIQUE INDEX idx_app_user_referral_code ON app_user(referral_code);
