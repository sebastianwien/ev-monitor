-- Oeffentlich teilbare Ladekurven.
--
-- Opt-in pro Ladung: der Token entsteht erst, wenn der Besitzer diese eine
-- Ladung teilt, und macht ausschliesslich sie oeffentlich. Widerruf setzt ihn
-- zurueck auf NULL, die URL ist danach tot. Loeschen des Logs nimmt ihn mit.
--
-- Bewusst ein eigener Zufalls-Token statt der Log-UUID: die oeffentliche URL
-- soll keine interne ID preisgeben und nach einem Widerruf nicht durch einen
-- erneuten Share wieder gueltig werden.
ALTER TABLE ev_log ADD COLUMN share_token VARCHAR(16);
ALTER TABLE ev_log ADD COLUMN share_created_at TIMESTAMP;

-- Partial unique: nur geteilte Zeilen belegen den Index, die grosse Mehrheit
-- ohne Token bleibt draussen.
CREATE UNIQUE INDEX ux_ev_log_share_token
    ON ev_log (share_token)
    WHERE share_token IS NOT NULL;
