-- V33: Add source_entity_id to coin_log for tracking which EvLog triggered a coin award.
-- Nullable UUID — null for non-log-related coin events (car created, referral, etc.)
-- Enables coin deduction on log deletion: SUM(amount) WHERE source_entity_id = :logId

ALTER TABLE coin_log ADD COLUMN source_entity_id UUID;

CREATE INDEX idx_coin_log_source_entity_id ON coin_log(source_entity_id);
