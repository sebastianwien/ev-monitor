-- Name ist Modell plus Trim, die Generation steckt nur im Zeitraum (Entscheidung 16.10.2026).
-- Gleicher Name und gleiche Brutto-kWh mit anderem Start sind zwei Zeilen, deshalb gehört available_from in den Schlüssel.
-- Blue/Green: der neue Schlüssel ist lockerer als der alte. Alter Code prüft im Service weiter streng und schreibt nichts,
-- was hier verboten wäre. NULLS NOT DISTINCT hält Altzeilen ohne Start wie bisher eindeutig.
ALTER TABLE vehicle_specification DROP CONSTRAINT uq_vehicle_spec;
ALTER TABLE vehicle_specification ADD CONSTRAINT uq_vehicle_spec
    UNIQUE NULLS NOT DISTINCT (car_brand, car_model, battery_capacity_kwh, variant_name, wltp_type, rating_source, available_from);
