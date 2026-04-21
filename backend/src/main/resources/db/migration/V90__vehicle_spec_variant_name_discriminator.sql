-- V90: variant_name als Discriminator im Unique Constraint
-- Ermöglicht mehrere Einträge pro kWh-Wert (z.B. Model 3 RWD 79kWh vs AWD 79kWh)

-- NULLs füllen: bestehende Einträge ohne Variante bekommen leeren String als Sentinel
UPDATE vehicle_specification SET variant_name = '' WHERE variant_name IS NULL;

ALTER TABLE vehicle_specification
    ALTER COLUMN variant_name SET NOT NULL,
    ALTER COLUMN variant_name SET DEFAULT '',
    ADD COLUMN available_from DATE NULL,
    ADD COLUMN available_to   DATE NULL;

ALTER TABLE vehicle_specification DROP CONSTRAINT uq_vehicle_spec;

ALTER TABLE vehicle_specification ADD CONSTRAINT uq_vehicle_spec
    UNIQUE (car_brand, car_model, battery_capacity_kwh, variant_name, wltp_type, rating_source);
