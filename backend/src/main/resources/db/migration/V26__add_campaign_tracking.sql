-- Add campaign tracking fields for marketing attribution (utm_*)
ALTER TABLE app_user ADD COLUMN utm_source VARCHAR(100);
ALTER TABLE app_user ADD COLUMN utm_medium VARCHAR(100);
ALTER TABLE app_user ADD COLUMN utm_campaign VARCHAR(100);

COMMENT ON COLUMN app_user.utm_source IS 'Campaign source (e.g. reddit, instagram, newsletter)';
COMMENT ON COLUMN app_user.utm_medium IS 'Campaign medium (e.g. cpc, organic, social)';
COMMENT ON COLUMN app_user.utm_campaign IS 'Campaign name (e.g. launch_march_2026)';

-- Index for campaign analytics queries
CREATE INDEX idx_app_user_utm_source ON app_user(utm_source) WHERE utm_source IS NOT NULL;
CREATE INDEX idx_app_user_utm_campaign ON app_user(utm_campaign) WHERE utm_campaign IS NOT NULL;
