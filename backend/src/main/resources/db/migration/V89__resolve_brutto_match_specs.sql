-- V89: Brutto-Match Einträge auflösen
-- Cars die auf eine Brutto-Match-Spec zeigen werden auf die kanonische Spec (Netto-Wert) umgezeigt.
-- Danach werden die Brutto-Match-Einträge gelöscht.
-- Betrifft: ENYAQ 82, ENYAQ_COUPE 82, MODEL_Y 78.40, MODEL_Y 82, E_UP 36.80

WITH brutto_to_canonical AS (
    SELECT DISTINCT ON (b.id)
        b.id    AS brutto_id,
        c.id    AS canonical_id
    FROM vehicle_specification b
    JOIN vehicle_specification c
        ON  c.car_brand              = b.car_brand
        AND c.car_model              = b.car_model
        AND c.net_battery_capacity_kwh = b.net_battery_capacity_kwh
        AND c.wltp_type              = b.wltp_type
        AND c.variant_name NOT LIKE '%Brutto%'
    WHERE b.variant_name LIKE '%Brutto%'
    ORDER BY b.id, c.battery_capacity_kwh ASC
)
UPDATE car
SET vehicle_specification_id = btc.canonical_id
FROM brutto_to_canonical btc
WHERE car.vehicle_specification_id = btc.brutto_id;

DELETE FROM vehicle_specification WHERE variant_name LIKE '%Brutto%';

-- MODEL_Y Duplikate entfernen (je zwei identische Einträge bei 60, 75, 79 kWh)
-- Schritt 1: Cars die auf einen Duplikat-Eintrag zeigen auf den surviving Eintrag umzeigen
WITH model_y_ranked AS (
    SELECT id,
           FIRST_VALUE(id) OVER (
               PARTITION BY car_brand, car_model, battery_capacity_kwh, wltp_type
               ORDER BY created_at ASC, id ASC
           ) AS surviving_id
    FROM vehicle_specification
    WHERE car_model = 'MODEL_Y'
)
UPDATE car
SET vehicle_specification_id = model_y_ranked.surviving_id
FROM model_y_ranked
WHERE car.vehicle_specification_id = model_y_ranked.id
  AND model_y_ranked.id != model_y_ranked.surviving_id;

-- Schritt 2: Duplikate löschen
DELETE FROM vehicle_specification
WHERE id IN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY car_brand, car_model, battery_capacity_kwh, wltp_type
                   ORDER BY created_at ASC, id ASC
               ) AS rn
        FROM vehicle_specification
        WHERE car_model = 'MODEL_Y'
    ) ranked
    WHERE rn > 1
);
