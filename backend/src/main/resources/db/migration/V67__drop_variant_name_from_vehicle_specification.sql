-- variant_name is now derived from the CarBrand.CarModel enum at runtime.
-- The enum is the single source of truth — no sync required with the DB.
ALTER TABLE vehicle_specification DROP COLUMN IF EXISTS variant_name;
