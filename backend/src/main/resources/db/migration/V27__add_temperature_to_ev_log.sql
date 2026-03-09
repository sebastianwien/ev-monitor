-- Add temperature at time of charging (fetched async from Open-Meteo, nullable = best-effort)
ALTER TABLE ev_log ADD COLUMN temperature_celsius FLOAT;

COMMENT ON COLUMN ev_log.temperature_celsius IS 'Ambient temperature in °C at charging location and time (sourced from Open-Meteo)';
