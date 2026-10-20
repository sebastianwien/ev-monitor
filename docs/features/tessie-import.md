# Tessie Import

Importiert Lade- und Fahrtdaten aus einem Tessie-Account (https://tessie.com)
in EV Monitor. Funktioniert für jeden User mit eigenem Tessie API-Token,
unabhängig vom EV-Monitor-Plan.

## User Flow

1. **Token eingeben** - Tessie API-Token aus `dash.tessie.com/settings/api`. Wird nicht persistiert.
2. **VIN-Auswahl** - Tessie liefert alle Fahrzeuge im Account. Default: alle ausgewählt.
3. **Auto-Zuordnung** - Pro VIN wählt der User welches Auto aus seiner Garage gemeint ist (Pflicht).
   Bei genau einem aktiven Auto wird automatisch zugeordnet.
4. **Import** - Pro VIN wird ein API-Call gemacht. Rohdaten werden persistiert und sofort
   in `ev_log` + `ev_trip` verarbeitet.
5. **Done** - User sieht pro VIN die Anzahl erstellter Ladevorgänge und Fahrten.

## Was wird importiert

### Charges -> ev_log
- `energy_added` -> `kwh_at_vehicle` (kWh netto in Akku)
- `started_at` -> `logged_at`
- `odometer` (Meilen) -> `odometer_km` (× 1.60934)
- `starting_battery` / `ending_battery` -> `soc_start_percent` / `soc_after_charge_percent`
- `latitude`/`longitude` -> `geohash` (Precision 7 für public, 6 für privat)
- `data_source` = `TESSIE`, `measurement_type` = `AT_VEHICLE`, `include_in_statistics` = true

### Drives -> ev_trip
- `started_at` / `ended_at` -> `trip_started_at` / `trip_ended_at`
- `starting_battery` / `ending_battery` -> `soc_start` / `soc_end`
- `starting_odometer` / `ending_odometer` (Meilen × 1.60934) -> `odometer_start_km` / `odometer_end_km`
- `odometer_distance` (Meilen × 1.60934) -> `distance_km`
- `energy_used` -> `estimated_consumed_kwh`
- `starting_latitude`/`starting_longitude` -> `location_start_geohash` (Precision 6)
- `ending_latitude`/`ending_longitude` -> `location_end_geohash` (Precision 6)
- `average_outside_temperature` -> `outside_temp_celsius`
- `average_speed` -> `route_type` (>= 90 km/h = HIGHWAY, < 60 km/h = CITY, sonst COMBINED)
- `data_source` = `TESSIE`, `status` = `COMPLETED`

## Public-/Private-Klassifikation (Charges)

- `is_supercharger` oder `is_fast_charger` -> public, `charging_type` = `DC`
- AC mit effektiver Leistung > 11 kW -> public (z.B. 22 kW Aldi/REWE)
- AC mit effektiver Leistung <= 11 kW -> privat (Heimladung)
- Effektive Leistung = max(Tessie `charger_power`, abgeleitet aus `energy_added` / Dauer)

UI-Hinweis im Mapping-Schritt informiert den User; Klassifikation kann nachträglich
pro Eintrag im EV-Monitor korrigiert werden.

## Merge-Logik

Tessie zerschneidet zusammenhängende Ladevorgänge und Fahrten oft in viele Sub-Sessions.

- **Charges** mit < 6 min Pause am gleichen Standort (Lat/Lon auf 3 Dezimalen gerundet)
  werden zu einem `ev_log` zusammengefasst. `kwh` summiert, `started_at` vom ersten,
  `ended_at`/`soc_end`/`odometer` vom letzten.
- **Drives** mit < 6 min Pause werden zu einer `ev_trip` zusammengefasst. Distanz und Energie
  summiert, Mikro-Trips < 0.5 km nach Merge werden verworfen (Parkmanöver/False-Positives).

## Architektur

Two-Stage:
- **Stage A (Postgres SQL):** Window-Function-Aggregation aus `tessie_raw_imports`
  (`process_charges_merge.sql`, `process_drives_merge.sql`).
- **Stage B (Java):** Iteriert merged Rows, berechnet Geohash über `ch.hsr.geohash.GeoHash`
  (single source of truth, identisch zu `EvLogService`), klassifiziert public/private,
  batch-insert via `JdbcTemplate`.

Anschließend leitet `RouteTypeDerivationService` den Streckentyp der neu angelegten Ladungen aus den
Fahrten ab (dominanter Typ nach km zwischen zwei Ladungen, Herkunft `TRIPS`, siehe
`consumption-calculation.md`, Abschnitt Streckentyp).

Eine Import-Anfrage läuft synchron in einer einzigen Transaktion:
Tessie API -> Roh-Insert -> Processor -> ev_log/ev_trip. Bei Fehler wird komplett
zurückgerollt.

## Idempotenz

- `tessie_raw_imports` hat Unique-Key `(user_id, vin, type, tessie_id)` -
  doppelter Roh-Insert wird via `ON CONFLICT DO NOTHING` ignoriert.
- Verarbeitete Rohdaten werden mit `processed = true` markiert.
  Re-Run produziert keine neuen Rows.
- `ev_log` hat Unique-Constraint `(car_id, logged_at, data_source)` -
  schützt zusätzlich gegen Doubletten.

## Security

- **Ownership-Check** im Controller: `carId` muss zum authentifizierten User gehören.
  Sowohl bei nicht existierender als auch bei fremder `carId` wird 404 zurückgegeben
  (keine Existenz-Leaks).
- Defense-in-depth: `TessieProcessorService.processForCar` validiert die Zuordnung erneut
  und wirft `IllegalArgumentException` bei Mismatch.
- Token wird nicht persistiert.
- Geohash-Präzision begrenzt GPS-Daten DSGVO-konform (max. ~150 m public, ~600 m privat).

## Endpoints

- `POST /api/import/tessie/vehicles` - Body `{ "token": "..." }` -> Liste der VINs.
- `POST /api/import/tessie/import` - Body `{ "token": "...", "vin": "...", "carId": "..." }`
  -> `TessieImportResult { drivesImported, chargesImported, skipped, evLogsCreated, evTripsCreated }`.

## Out of Scope

- Tessie Battery-Health-Endpoint (SoH wird automatisch über die bestehende Auto-Detection
  aus den importierten Charges abgeleitet).
- Phantom Drain (existiert bereits im EV Monitor und läuft auf den neuen `ev_trip`-Rows automatisch).
- Reprocessing-UI für bereits importierte Tessie-Daten.
