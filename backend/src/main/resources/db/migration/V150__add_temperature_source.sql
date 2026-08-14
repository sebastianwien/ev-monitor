-- Herkunft der Temperatur festhalten.
--
-- Bisher stand nur der Wert in der Zeile, nicht woher er kam. Damit liess sich nicht entscheiden,
-- ob ein Wert vom Fahrzeug gemessen oder von Open-Meteo geholt wurde - und genau das braucht der
-- Nachtjob, seit die Wetterabfrage eine Zeitzonen-Korrektur bekommen hat: alle mit der alten
-- Logik geholten Werte lagen im Sommer zwei Stunden daneben und muessen einmalig neu geholt werden.
--
-- NULL bedeutet ab jetzt "Herkunft unbekannt" und ist zugleich der Arbeitsvorrat des Jobs.

ALTER TABLE ev_log  ADD COLUMN temperature_source  varchar(16);
ALTER TABLE ev_trip ADD COLUMN outside_temp_source varchar(16);

COMMENT ON COLUMN ev_log.temperature_source IS
    'MEASURED = vom Fahrzeug gemessen, FORECAST = von Open-Meteo geholt, AMBIGUOUS = Herkunft nicht entscheidbar, NULL = unbekannt (Arbeitsvorrat des Backfill-Jobs)';
COMMENT ON COLUMN ev_trip.outside_temp_source IS
    'MEASURED = vom Fahrzeug gemessen, FORECAST = von Open-Meteo geholt, AMBIGUOUS = Herkunft nicht entscheidbar, NULL = unbekannt (Arbeitsvorrat des Backfill-Jobs)';

-- Klassifikation der Altdaten. Reine SQL-Arbeit, keine Wetterabfrage.
--
-- Die Herkunft wird am Nachkommawert erschlossen: Open-Meteo liefert Zehntelgrade (gleichverteilt
-- ueber .0 bis .9), Fahrzeuge liefern halbe Grade. Ein Wert, der kein Vielfaches von 0,5 ist, kann
-- also nicht gemessen sein - er bleibt NULL und wird neu geholt. Ein Wert auf .0/.5 kann beides
-- sein: zwei von zehn Zehnteln sehen zufaellig wie eine Messung aus. Diese Zeilen werden nicht
-- geraten, sondern als AMBIGUOUS markiert und in Ruhe gelassen.

-- MEASURED nur dort, wo neben der Verteilung ein struktureller Beleg existiert:

-- EU-Data-Act-Importe bringen die Temperatur des Herstellers mit (alle 10 Zeilen auf halben Graden).
UPDATE ev_log SET temperature_source = 'MEASURED'
 WHERE temperature_celsius IS NOT NULL
   AND data_source = 'EU_DATA_ACT_IMPORT'
   AND mod((temperature_celsius * 10)::int, 5) = 0;

-- Tesla-Live-Trips: dieser Pfad hatte den Rueckblick in den Signalstrom schon immer, entsprechend
-- liegen 99% der Werte auf halben Graden.
UPDATE ev_trip SET outside_temp_source = 'MEASURED'
 WHERE outside_temp_celsius IS NOT NULL
   AND data_source = 'TESLA_LIVE'
   AND mod((outside_temp_celsius * 10)::int, 5) = 0;

-- Alles uebrige auf halben Graden: geprueft, nicht entscheidbar.
UPDATE ev_log SET temperature_source = 'AMBIGUOUS'
 WHERE temperature_celsius IS NOT NULL
   AND temperature_source IS NULL
   AND mod((temperature_celsius * 10)::int, 5) = 0;

UPDATE ev_trip SET outside_temp_source = 'AMBIGUOUS'
 WHERE outside_temp_celsius IS NOT NULL
   AND outside_temp_source IS NULL
   AND mod((outside_temp_celsius * 10)::int, 5) = 0;

-- Der Job fragt genau diese Menge ab, neueste zuerst.
CREATE INDEX idx_ev_log_temperature_backfill
    ON ev_log (logged_at DESC)
 WHERE geohash IS NOT NULL
   AND (temperature_celsius IS NULL OR temperature_source IS NULL);

CREATE INDEX idx_ev_trip_temperature_backfill
    ON ev_trip (trip_started_at DESC)
 WHERE (location_start_geohash IS NOT NULL OR location_end_geohash IS NOT NULL)
   AND (outside_temp_celsius IS NULL OR outside_temp_source IS NULL);
