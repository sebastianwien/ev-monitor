-- Speichert die downgesampelte Ladekurve (~30 Punkte als JSON-Array
-- [{"ts": <epochMs>, "kw": <number>}, ...]) bei Session-Finalize. Quelle:
-- ACChargingPower / DCChargingPower aus vehicle_signal_events, ueberlebt
-- damit das 28-Tage-Retention-Limit der raw events. Nur fuer Sessions, deren
-- Connector Live-Power streamt (aktuell Tesla FULL-Profil); NULL fuer alle
-- anderen DataSources.
ALTER TABLE ev_log ADD COLUMN power_curve_points jsonb;
