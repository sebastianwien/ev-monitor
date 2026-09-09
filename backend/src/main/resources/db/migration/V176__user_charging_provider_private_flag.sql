-- Kennzeichnet eine Ladekarte als privaten Heimtarif (eigene Wallbox / Haushaltsstrom).
--
-- Warum: Import-Quellen wie XPeng liefern weder Ort noch Preis. Ohne Geohash greift der
-- ortsbasierte Tarif nie, und der Nutzer muesste jede importierte Heimladung einzeln
-- nachtragen. Die ausdrueckliche "privat"-Markierung ist die Aussage des Nutzers, dass
-- dieser Listenpreis fuer seine nicht-oeffentlichen Ladungen gilt - und nur damit darf
-- automatisch bepreist werden.
ALTER TABLE user_charging_providers
    ADD COLUMN is_private BOOLEAN NOT NULL DEFAULT FALSE;

-- Teilindex: der Heimtarif-Lookup laeuft bei jedem Log-Import, trifft aber nur wenige Zeilen.
CREATE INDEX idx_user_charging_providers_private
    ON user_charging_providers (user_id)
    WHERE is_private AND deleted_at IS NULL;
