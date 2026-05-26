-- XPeng AutoSync Phase 2: last_request_sent_at wurde in V123 vergessen
ALTER TABLE xpeng_connection
    ADD COLUMN IF NOT EXISTS last_request_sent_at TIMESTAMP;

COMMENT ON COLUMN xpeng_connection.last_request_sent_at IS 'Zeitpunkt der letzten automatisch gesendeten DA-Anfrage an XPeng. NULL solange noch keine Anfrage gesendet wurde.';
