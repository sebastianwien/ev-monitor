-- Rename car.battery_capacity_kwh -> car.custom_net_capacity_kwh.
--
-- Hintergrund: Die Spalte haelt die vom User selbst eingegebene Batteriekapazitaet.
-- Nach dem Backfill der vehicle_specification.net_battery_capacity_kwh-Werte (2026-05-18)
-- ist klar, dass diese User-Eingabe ein Netto-Override darstellt, nicht ein Brutto-Nominal.
-- Der alte Name suggerierte faelschlich Brutto. Wird in Car.getNominalNetCapacityKwh() als
-- Fallback genutzt, falls keine Spec verknuepft ist.

ALTER TABLE car RENAME COLUMN battery_capacity_kwh TO custom_net_capacity_kwh;

COMMENT ON COLUMN car.custom_net_capacity_kwh IS
    'User-supplied net (usable) battery capacity in kWh. Only consulted when no vehicle_specification is linked - otherwise vehicle_specification.net_battery_capacity_kwh wins.';
