ALTER TABLE ev_log
  ALTER COLUMN soc_after_charge_percent TYPE NUMERIC(5,2),
  ALTER COLUMN soc_start_percent TYPE NUMERIC(5,2);

ALTER TABLE ev_log DROP CONSTRAINT IF EXISTS chk_soc_after_charge_range;
ALTER TABLE ev_log ADD CONSTRAINT chk_soc_after_charge_range
  CHECK (soc_after_charge_percent IS NULL OR (soc_after_charge_percent >= 0 AND soc_after_charge_percent <= 100));
