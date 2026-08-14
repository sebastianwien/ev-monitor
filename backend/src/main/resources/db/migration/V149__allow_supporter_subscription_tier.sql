-- Widens the tier check constraint for SUPPORTER.
--
-- SubscriptionTier.SUPPORTER exists in the domain since the analytics-only upsell was
-- added, but V112's constraint still only allowed NONE/AUTOSYNC/AUTOSYNC_LIVE. Any write
-- of the new tier - the Stripe webhook confirming a Supporter purchase above all - would
-- have failed with a constraint violation.
--
--   SUPPORTER - analytics-only: unlocks the premium analytics view (energy split, phantom
--               drain, power curves) without any telemetry/AutoSync entitlement. Orthogonal
--               to the AutoSync ladder, so is_premium stays false for these users
--               (SubscriptionTier.grantsTelemetry() excludes it).
--
-- No data migration: no row can hold 'SUPPORTER' yet, precisely because the constraint
-- rejected it.

ALTER TABLE app_user
    DROP CONSTRAINT IF EXISTS app_user_subscription_tier_check;

ALTER TABLE app_user
    ADD CONSTRAINT app_user_subscription_tier_check
    CHECK (subscription_tier IN ('NONE', 'AUTOSYNC', 'AUTOSYNC_LIVE', 'SUPPORTER'));
