#!/bin/bash
# Migrates smartcar_webhook_raw_log from ev_monitor DB to ev_connectors DB.
# Run this BEFORE deploying backend V103 (which drops the table from ev_monitor).
#
# Usage: bash scripts/migrate_smartcar_raw_log.sh
# Requires: SSH access to prod (ssh ihle@ev-monitor.net -p 2222)

set -e

echo "=== SmartCar Raw Log Migration: ev_monitor -> ev_connectors ==="

ssh ihle@ev-monitor.net -p 2222 bash << 'ENDSSH'
set -e

echo "[1/3] Exporting from ev_monitor with is_charging + energy_added_kwh extracted from JSONB..."
docker exec ev-monitor-db-1 psql -U evmonitor -d ev_monitor -c "
\copy (
  SELECT
    id,
    received_at,
    event_id,
    smartcar_vehicle_id,
    make,
    model,
    vehicle_year,
    triggers::text,
    signals::text,
    soc_percent,
    odometer_km,
    location_geohash,
    outside_temp_celsius,
    mode,
    (SELECT (elem -> 'body' ->> 'value')::boolean
     FROM jsonb_array_elements(signals) AS elem
     WHERE elem ->> 'name' = 'IsCharging' LIMIT 1) AS is_charging,
    (SELECT (elem -> 'body' ->> 'value')::numeric
     FROM jsonb_array_elements(signals) AS elem
     WHERE elem ->> 'name' = 'EnergyAdded' LIMIT 1) AS energy_added_kwh
  FROM smartcar_webhook_raw_log
  ORDER BY received_at
) TO '/tmp/smartcar_raw_log_migration.csv' CSV HEADER
"

COUNT=$(docker exec ev-monitor-db-1 psql -U evmonitor -d ev_monitor -t -c "SELECT COUNT(*) FROM smartcar_webhook_raw_log;")
echo "  Exported $COUNT rows"

echo "[2/3] Importing into ev_connectors via temp table..."
docker exec ev-monitor-db-1 psql -U evmonitor -d ev_connectors -c "
CREATE TEMP TABLE smartcar_raw_import (LIKE smartcar_webhook_raw_log);

COPY smartcar_raw_import
  (id, received_at, event_id, smartcar_vehicle_id, make, model, vehicle_year,
   triggers, signals, soc_percent, odometer_km, location_geohash,
   outside_temp_celsius, mode, is_charging, energy_added_kwh)
FROM '/tmp/smartcar_raw_log_migration.csv' CSV HEADER;

INSERT INTO smartcar_webhook_raw_log
  (id, received_at, event_id, smartcar_vehicle_id, make, model, vehicle_year,
   triggers, signals, soc_percent, odometer_km, location_geohash,
   outside_temp_celsius, mode, is_charging, energy_added_kwh)
SELECT
  id, received_at, event_id, smartcar_vehicle_id, make, model, vehicle_year,
  triggers, signals, soc_percent, odometer_km, location_geohash,
  outside_temp_celsius, mode, is_charging, energy_added_kwh
FROM smartcar_raw_import
ON CONFLICT (event_id) DO NOTHING;
"

echo "[3/3] Backfilling car_id + user_id from smartcar_connections..."
docker exec ev-monitor-db-1 psql -U evmonitor -d ev_connectors -c "
UPDATE smartcar_webhook_raw_log w
SET car_id  = c.car_id,
    user_id = c.user_id
FROM smartcar_connections c
WHERE c.smartcar_vehicle_id = w.smartcar_vehicle_id
  AND w.car_id IS NULL;
"

MIGRATED=$(docker exec ev-monitor-db-1 psql -U evmonitor -d ev_connectors -t -c "SELECT COUNT(*) FROM smartcar_webhook_raw_log;")
echo "  ev_connectors now has $MIGRATED rows"

rm /tmp/smartcar_raw_log_migration.csv
echo "=== Migration complete. Now deploy backend V103 (DROP TABLE) ==="
ENDSSH
