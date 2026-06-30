-- Stable anchor for the "AutoSync satisfaction" survey mail. Set once, on the
-- first inactive->active subscription transition (the AutoSync purchase moment),
-- and never overwritten on renewals. Unlike subscription_period_end (which moves
-- every billing cycle) this gives a fixed point to schedule "N days after the
-- 7-day trial" off of.
ALTER TABLE app_user ADD COLUMN autosync_started_at TIMESTAMP;
