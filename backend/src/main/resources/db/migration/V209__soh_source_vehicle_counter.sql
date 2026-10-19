-- Neue SoH-Herkunft VEHICLE_COUNTER: Summe der Fahrt-Energiezähler des Fahrzeugs durch Summe der
-- SoC-Deltas über viele Fahrten (Polestar 3). sample_size = gezählte Fahrten, soc_hub_percent = Gesamthub
-- (mehrere hundert Prozent). Nur Erweiterung der Constraints, abwärtskompatibel (Expand).
ALTER TABLE car_battery_soh_log DROP CONSTRAINT chk_car_battery_soh_log_source;
ALTER TABLE car_battery_soh_log
    ADD CONSTRAINT chk_car_battery_soh_log_source
    CHECK (source IN ('MANUAL', 'CHARGE_LOG', 'VEHICLE_BMS', 'VEHICLE_COUNTER', 'UNKNOWN'));

ALTER TABLE car_battery_soh_log DROP CONSTRAINT chk_car_battery_soh_log_sample_size;
ALTER TABLE car_battery_soh_log
    ADD CONSTRAINT chk_car_battery_soh_log_sample_size
    CHECK ((source IN ('CHARGE_LOG', 'VEHICLE_COUNTER') AND sample_size >= 1)
        OR (source NOT IN ('CHARGE_LOG', 'VEHICLE_COUNTER') AND sample_size IS NULL));

ALTER TABLE car_battery_soh_log DROP CONSTRAINT IF EXISTS chk_car_battery_soh_log_soc_hub;
ALTER TABLE car_battery_soh_log
    ADD CONSTRAINT chk_car_battery_soh_log_soc_hub
    CHECK (soc_hub_percent IS NULL OR (source IN ('CHARGE_LOG', 'VEHICLE_COUNTER') AND soc_hub_percent > 0));

COMMENT ON COLUMN car_battery_soh_log.source IS
    'MANUAL = vom Nutzer eingetragen, CHARGE_LOG = gewichteter Median aus Ladevorgaengen, VEHICLE_BMS = aus Fahrzeugdaten (Tesla EnergyRemaining), VEHICLE_COUNTER = aus den Verbrauchszaehlern des Fahrzeugs, UNKNOWN = vor V147 erfasst';
COMMENT ON COLUMN car_battery_soh_log.sample_size IS
    'CHARGE_LOG: Ladevorgaenge im Median-Fenster; VEHICLE_COUNTER: summierte Fahrten';
