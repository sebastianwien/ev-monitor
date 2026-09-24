-- Soft-Delete für Ladevorgänge. Gelöschte Logs bleiben als Tombstone erhalten, damit ein
-- erneuter Import (z. B. EUDA AutoSync) sie nicht wieder anlegt und ein Restore möglich ist.
ALTER TABLE ev_log ADD COLUMN deleted_at TIMESTAMP;
CREATE INDEX idx_ev_log_deleted_at ON ev_log(deleted_at) WHERE deleted_at IS NOT NULL;
