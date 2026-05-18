-- V119: add energy_source marker to ev_log
--
-- Purpose: capture the provenance of kwh_charged / kwh_at_vehicle so the SoH
-- auto-detector can exclude SOC_INFERRED rows (kwh computed from SoC-delta x
-- effective_capacity, e.g. EU non-Tesla Smartcar brands without `energyAdded`).
-- Feeding such rows into BatterySohAutoDetector creates a self-referential loop
-- that drifts SoH by the brutto/netto capacity ratio per run.
--
-- This migration is INTENTIONALLY no-op for production behaviour:
--   - Column is nullable; existing rows stay NULL.
--   - BatterySohAutoDetector treats NULL as trusted (backwards-compatible).
--   - Write side (Connector + REST DTO) is wired in a follow-up step.
ALTER TABLE ev_log ADD COLUMN energy_source VARCHAR(20);

ALTER TABLE ev_log ADD CONSTRAINT ev_log_energy_source_check
    CHECK (energy_source IS NULL OR energy_source IN ('OEM_MEASURED', 'SOC_INFERRED', 'USER_INPUT', 'WALLBOX'));

COMMENT ON COLUMN ev_log.energy_source IS
    'Provenance of kwh_charged/kwh_at_vehicle: OEM_MEASURED (real OEM reading), SOC_INFERRED (calculated from SoC delta x effective capacity, no real energy measurement), USER_INPUT (manual entry), WALLBOX (from OCPP/Wallbox meter). NULL for legacy rows before V119.';
