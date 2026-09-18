-- DSGVO-Kontolöschung: Autos, Ladevorgänge und Trips bleiben anonymisiert erhalten, damit die
-- Community-Statistik (Public-Model-Seiten) nicht schrumpft. Der Personenbezug wird gekappt:
-- user_id NULL, Kennzeichen/Bild/Geohashes/Freitexte NULL, Zeitstempel auf Tag gerundet.
-- Der bisherige ON DELETE CASCADE bleibt bestehen - er greift nur noch für Autos, die beim
-- Löschen noch einen Besitzer haben (Anonymisierung läuft davor).
ALTER TABLE car ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE car ADD COLUMN anonymized_at TIMESTAMP;
ALTER TABLE ev_trip ALTER COLUMN user_id DROP NOT NULL;
