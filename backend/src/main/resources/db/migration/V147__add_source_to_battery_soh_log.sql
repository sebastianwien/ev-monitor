-- Herkunft eines SoH-Eintrags nachvollziehbar machen.
--
-- Bisher war nicht unterscheidbar, ob ein Wert manuell eingetragen, aus Ladelogs
-- geschaetzt oder vom Fahrzeug-BMS gemeldet wurde. Fuer die Anzeige im UI (Vertrauens-
-- Badge) und fuer jede spaetere Bereinigung ist das zwingend noetig.
--
-- Altbestand wird bewusst als UNKNOWN markiert und NICHT nachtraeglich klassifiziert:
-- die dafuer noetige Heuristik (alten Algorithmus nachrechnen und auf Uebereinstimmung
-- pruefen) setzt unveraenderte Ladelogs voraus, was nicht garantiert ist.

ALTER TABLE car_battery_soh_log
    ADD COLUMN source      VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN sample_size INTEGER;

-- Default nur fuer das Backfill der Bestandszeilen. Danach entfernen, damit ein
-- vergessenes Mapping im Code als Constraint-Verletzung auffaellt statt still
-- UNKNOWN zu schreiben.
ALTER TABLE car_battery_soh_log ALTER COLUMN source DROP DEFAULT;

ALTER TABLE car_battery_soh_log
    ADD CONSTRAINT chk_car_battery_soh_log_source
    CHECK (source IN ('MANUAL', 'CHARGE_LOG', 'VEHICLE_BMS', 'UNKNOWN'));

-- sample_size zaehlt die Ladevorgaenge im gewichteten Median-Fenster und ist damit
-- nur fuer CHARGE_LOG definiert.
ALTER TABLE car_battery_soh_log
    ADD CONSTRAINT chk_car_battery_soh_log_sample_size
    CHECK ((source = 'CHARGE_LOG' AND sample_size >= 1) OR (source <> 'CHARGE_LOG' AND sample_size IS NULL));

COMMENT ON COLUMN car_battery_soh_log.source IS
    'MANUAL = vom Nutzer eingetragen, CHARGE_LOG = gewichteter Median aus Ladevorgaengen, VEHICLE_BMS = vom Fahrzeug gemeldet, UNKNOWN = vor V147 erfasst';
COMMENT ON COLUMN car_battery_soh_log.sample_size IS
    'Anzahl Ladevorgaenge im Median-Fenster; nur bei source = CHARGE_LOG gesetzt';
