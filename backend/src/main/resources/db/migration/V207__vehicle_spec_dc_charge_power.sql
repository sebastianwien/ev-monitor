-- DC-Ladeleistung je Spec-Zeile, Quelle ev-database.org. Nullable ohne Default (Blue/Green-kompatibel).
ALTER TABLE vehicle_specification
    ADD COLUMN dc_max_power_kw NUMERIC(5,1) NULL,
    ADD COLUMN dc_avg_power_10_80_kw NUMERIC(5,1) NULL;

COMMENT ON COLUMN vehicle_specification.dc_max_power_kw IS 'Maximale DC-Ladeleistung in kW, EVDB "Charge Power (max)". Nur Datenblatt.';
COMMENT ON COLUMN vehicle_specification.dc_avg_power_10_80_kw IS 'Mittlere DC-Ladeleistung 10-80 % in kW, EVDB "Charge Power (10-80%)". Basis der Reisezeit-Rechnung.';
