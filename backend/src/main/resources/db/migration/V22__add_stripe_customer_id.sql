ALTER TABLE app_user ADD COLUMN stripe_customer_id VARCHAR(255);
CREATE UNIQUE INDEX idx_app_user_stripe_customer_id ON app_user(stripe_customer_id) WHERE stripe_customer_id IS NOT NULL;
