-- Tessie-Import: Odometer und Trip-Distanzen waren faelschlich von Meilen nach km konvertiert.
--
-- Ursache: TessieClient ruft die API mit distance_format=km ab, die Merge-SQL multiplizierte die
-- Werte danach trotzdem mit 1.60934. Odometer und Distanzen sind dadurch um diesen Faktor zu gross,
-- der daraus berechnete Verbrauch entsprechend ~38% zu niedrig.
--
-- Betrifft BEIDE Quellen:
--   - Live-API-Import (4 Accounts)
--   - V101-Foxcar-Dump (17 Fahrzeuge) - dessen Rohdaten lagen entgegen der damaligen Annahme
--     ebenfalls in Kilometern vor. Belegt ueber die implizite Durchschnittsgeschwindigkeit
--     (distance_km / Fahrzeit): 83 Trips lagen ueber 200 km/h im Schnitt, einer bei 256 km/h -
--     physikalisch ausgeschlossen. Nach der Korrektur: Schnitt ~73 km/h, Maximum ~159 km/h.
--
-- Der Code-Fix (Multiplikation entfernt) ist Teil desselben Deploys, neue Importe schreiben also
-- bereits korrekte Werte. avg_speed_kmh/max_speed_kmh sind nicht betroffen - sie wurden nie
-- konvertiert.
--
-- Einmalig und bewusst nicht idempotent: ein zweiter Lauf wuerde erneut teilen. Flyway stellt
-- sicher, dass die Migration genau einmal ausgefuehrt wird.

UPDATE ev_log
SET odometer_km = ROUND(odometer_km / 1.60934)
WHERE data_source = 'TESSIE'
  AND odometer_km IS NOT NULL;

UPDATE ev_trip
SET odometer_start_km = ROUND(odometer_start_km / 1.60934, 1),
    odometer_end_km   = ROUND(odometer_end_km   / 1.60934, 1),
    distance_km       = ROUND(distance_km       / 1.60934, 3)
WHERE data_source = 'TESSIE';
