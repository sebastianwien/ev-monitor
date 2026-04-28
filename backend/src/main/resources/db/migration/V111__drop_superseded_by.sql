-- Remove the superseded_by concept entirely.
-- Duplicate detection was too implicit — 4 prod occurrences in total.
-- The 4 previously hidden logs become visible again.

ALTER TABLE ev_log DROP COLUMN IF EXISTS superseded_by;
