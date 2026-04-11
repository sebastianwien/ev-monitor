-- Soft-delete support for user_charging_providers.
-- deleted_at IS NULL = active; IS NOT NULL = soft-deleted.
-- ev_log.charging_provider_id uses ON DELETE SET NULL so historical FK references
-- survive even after the user removes the card from their portfolio.

ALTER TABLE user_charging_providers
    ADD COLUMN deleted_at TIMESTAMP NULL;
