-- Wärmepumpe Flag auf car-Tabelle
ALTER TABLE car ADD COLUMN has_heat_pump BOOLEAN NOT NULL DEFAULT false;

-- Batterie-SoH Verlaufstabelle
CREATE TABLE car_battery_soh_log (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    car_id      UUID NOT NULL REFERENCES car(id) ON DELETE CASCADE,
    soh_percent NUMERIC(5,2) NOT NULL CHECK (soh_percent BETWEEN 50 AND 100),
    recorded_at DATE NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_car_battery_soh_log_car_id ON car_battery_soh_log(car_id, recorded_at DESC);

-- Bestehende battery_degradation_percent Werte als ersten SoH-Eintrag migrieren
INSERT INTO car_battery_soh_log (id, car_id, soh_percent, recorded_at, created_at)
SELECT gen_random_uuid(), id, (100 - battery_degradation_percent), CURRENT_DATE, now()
FROM car
WHERE battery_degradation_percent IS NOT NULL
  AND battery_degradation_percent > 0;
