-- Add SoC (State of Charge) after charging to ev_log table
-- Allows precise consumption calculation: Start-SoC = End-SoC - (kWh_charged / battery_capacity × 100)
-- Consumption = (SoC₁ - SoC₂) × battery_capacity / Δ km

ALTER TABLE ev_log ADD COLUMN soc_after_charge_percent INTEGER;

-- Add check constraint: SoC must be between 0 and 100
ALTER TABLE ev_log ADD CONSTRAINT chk_soc_after_charge_range
    CHECK (soc_after_charge_percent IS NULL OR (soc_after_charge_percent >= 0 AND soc_after_charge_percent <= 100));

-- Create index for queries that filter/sort by SoC
CREATE INDEX idx_ev_log_soc ON ev_log(soc_after_charge_percent) WHERE soc_after_charge_percent IS NOT NULL;
