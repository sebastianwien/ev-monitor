-- Alle bestehenden logged_at-Werte auf Minuten truncaten (Sekunden = 00).
-- Hintergrund: Das Frontend sendet loggedAt ohne Sekunden, was zu Idempotency-Fehlern
-- beim Tesla-Re-Import nach manuellem Edit fuehrte. Der EvLog-Domain-Konstruktor truncated
-- ab sofort alle neuen Eintraege auf Minuten - diese Migration bringt die bestehenden
-- Daten auf denselben Stand.
-- Alle Rows in die Partition einbeziehen (auch bereits truncatete),
-- damit Kollisionen mit bestehenden 00:00:00-Eintraegen aufgeloest werden.
-- Rows die bereits die korrekte Zeit haben werden nicht angefasst (WHERE-Klausel).
WITH ranked AS (
    SELECT id,
           date_trunc('minute', logged_at) + ((ROW_NUMBER() OVER (
               PARTITION BY car_id, data_source, date_trunc('minute', logged_at)
               ORDER BY logged_at
           ) - 1) * INTERVAL '1 minute') AS new_logged_at
    FROM ev_log
)
UPDATE ev_log
SET logged_at = ranked.new_logged_at
FROM ranked
WHERE ev_log.id = ranked.id
  AND ev_log.logged_at != ranked.new_logged_at;
