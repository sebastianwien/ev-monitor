-- Introduces a tiered subscription model on top of the legacy boolean is_premium flag.
-- Tier values are kept as VARCHAR (not enum) so we can extend without an ALTER TYPE
-- (Postgres makes that painful in transactions). Domain-level enum SubscriptionTier
-- is the source of truth for valid values.
--
--   NONE          - free tier (default)
--   AUTOSYNC      - charging-only sync (Smartcar webhooks, Tesla CHARGING_ONLY profile)
--   AUTOSYNC_LIVE - everything plus Tesla FULL profile (trip-push, phantom-drain)
--
-- Existing premium users are backfilled to AUTOSYNC. is_premium stays in place during
-- the transition (pre-V112 callers and DB views still rely on it). A later migration
-- will drop the boolean once all writers go through the tier path.

ALTER TABLE app_user
    ADD COLUMN subscription_tier VARCHAR(32) NOT NULL DEFAULT 'NONE';

UPDATE app_user
    SET subscription_tier = 'AUTOSYNC'
    WHERE is_premium = true;

-- Sanity guard against typo'd values being inserted from rogue SQL.
ALTER TABLE app_user
    ADD CONSTRAINT app_user_subscription_tier_check
    CHECK (subscription_tier IN ('NONE', 'AUTOSYNC', 'AUTOSYNC_LIVE'));
