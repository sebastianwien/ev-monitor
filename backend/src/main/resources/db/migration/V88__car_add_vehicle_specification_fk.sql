-- V88: car bekommt nullable FK auf vehicle_specification
-- Auto-Match wo genau ein WLTP COMBINED Eintrag auf brand+model+capacity passt

ALTER TABLE car
    ADD COLUMN vehicle_specification_id UUID REFERENCES vehicle_specification(id);

-- Auto-Match: brand aus model-Enum ableiten ist nicht trivial in SQL,
-- daher matchen wir nur ueber car_model (UPPER) + battery_capacity_kwh
-- Bedingung: genau ein WLTP COMBINED Eintrag vorhanden (kein ambiguous match)
UPDATE car c
SET vehicle_specification_id = vs.id
FROM vehicle_specification vs
WHERE UPPER(c.model) = vs.car_model
  AND c.battery_capacity_kwh = vs.battery_capacity_kwh
  AND vs.wltp_type = 'COMBINED'
  AND vs.rating_source = 'WLTP'
  AND (
      SELECT COUNT(*)
      FROM vehicle_specification vs2
      WHERE vs2.car_model = UPPER(c.model)
        AND vs2.battery_capacity_kwh = c.battery_capacity_kwh
        AND vs2.wltp_type = 'COMBINED'
        AND vs2.rating_source = 'WLTP'
  ) = 1;

CREATE INDEX idx_car_vehicle_specification_id ON car(vehicle_specification_id);
