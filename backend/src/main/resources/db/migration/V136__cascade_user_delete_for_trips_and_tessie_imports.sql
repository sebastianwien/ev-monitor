-- Kontoloeschung war unvollstaendig bzw. schlug ganz fehl.
--
-- UserService.deleteAccount() ruft nur userRepository.delete(user) und verlaesst sich darauf, dass
-- die Datenbank alle abhaengigen Zeilen per CASCADE mitloescht. Zwei Tabellen taten das nicht:
--
--   tessie_raw_imports (V98) - Fremdschluessel ohne ON DELETE CASCADE (NO ACTION). Wer Tessie
--     verbunden hatte, konnte seinen Account gar nicht loeschen: Postgres brach mit einer
--     Fremdschluessel-Verletzung ab.
--
--   ev_trip - hatte ueberhaupt keinen Fremdschluessel auf app_user. Die Trips eines geloeschten
--     Nutzers blieben als Waisen liegen. Das ist ein DSGVO-Verstoss: personenbezogene Bewegungs-
--     daten (Geohashes, Zeitstempel, Strecken) ueberleben die Kontoloeschung.
--
-- Alle uebrigen elf Tabellen mit user_id cascaden korrekt.
--
-- Beide Datenbestaende haben ohne ihren Nutzer keinen Wert - Loeschen ist hier zugleich die
-- DSGVO-konforme Variante.

-- Waisen aus frueheren Kontoloeschungen. Muss vor dem Fremdschluessel laufen, sonst laesst er sich
-- nicht anlegen.
DELETE FROM ev_trip t
WHERE NOT EXISTS (SELECT 1 FROM app_user u WHERE u.id = t.user_id);

ALTER TABLE ev_trip
    ADD CONSTRAINT ev_trip_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE;

ALTER TABLE tessie_raw_imports
    DROP CONSTRAINT tessie_raw_imports_user_id_fkey;

ALTER TABLE tessie_raw_imports
    ADD CONSTRAINT tessie_raw_imports_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE;
