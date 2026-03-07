# Charging Logs (EvLog)

**Status:** ✅ Implementiert
**Last Updated:** 2026-03-07

## Overview

Core Feature zum Tracken von Ladevorgängen. User können kWh, Kosten, Dauer, Standort (Geohash), Odometer, Batteriestand und Max Charging Power erfassen.

## Domain Model

**Entity:** `EvLog.java`

**Fields:**
- `id` (UUID)
- `carId` (UUID, FK → Car)
- `kwhCharged` (BigDecimal) - Geladene Energie
- `costEur` (BigDecimal) - Kosten in Euro
- `chargeDurationMinutes` (Integer)
- `geohash` (String, 5 chars) - Privacy-First (kein GPS!)
- `odometerKm` (Integer, optional)
- `maxChargingPowerKw` (BigDecimal, optional) - z.B. 11kW, 50kW, 150kW
- **`socAfterChargePercent` (Integer 0-100, optional)** - Batteriestand nach dem Laden (seit V10)
- `loggedAt` (LocalDateTime) - Kann in der Vergangenheit liegen
- `dataSource` (String) - `USER_LOGGED` | `SPRITMONITOR_IMPORT` | `TESLA_IMPORT`
- `createdAt`, `updatedAt`

## Components

### Backend
- **EvLogService.java** - Business Logic
- **EvLogController.java** - REST API
- **EvLogRepository.java** - Domain Repository Interface
- **PostgresEvLogRepositoryImpl.java** - JPA Implementation

### Frontend
- **DashboardView.vue** - Log-Formular + Location Search
- **LogForm.vue** - Charging Log Form mit SoC-Feld
- **LocationSearch.vue** - Nominatim API Integration (Debounced)
- **api/evLogService.ts** - API Calls

## API Endpoints

### POST /api/logs
**Request:**
```json
{
  "carId": "uuid",
  "kwhCharged": 45.5,
  "costEur": 15.50,
  "chargeDurationMinutes": 62,
  "latitude": 52.520008,
  "longitude": 13.404954,
  "odometerKm": 22743,
  "maxChargingPowerKw": 50.0,
  "socAfterChargePercent": 80,
  "loggedAt": "2025-03-01T10:30:00"
}
```

**WICHTIG:** Frontend sendet `latitude/longitude`, Backend speichert **nur Geohash**!

**Response (201 Created):**
```json
{
  "id": "uuid",
  "carId": "uuid",
  "kwhCharged": 45.5,
  "costEur": 15.50,
  "chargeDurationMinutes": 62,
  "geohash": "u33db",
  "odometerKm": 22743,
  "maxChargingPowerKw": 50.0,
  "socAfterChargePercent": 80,
  "loggedAt": "2025-03-01T10:30:00",
  "createdAt": "2025-03-01T10:35:00",
  "updatedAt": "2025-03-01T10:35:00"
}
```

### GET /api/logs?carId={uuid}
Alle Logs für ein Fahrzeug (unsorted, TODO: Pagination).

**Response:** Array of `EvLogResponse`

### GET /api/logs/{id}
Einzelner Log (currently unused in Frontend).

### GET /api/logs/statistics
Siehe [Statistics & Heatmap](./statistics-heatmap.md).

## Geohashing Implementation

**Backend:** `EvLogService.java`

```java
import ch.hsr.geohash.GeoHash;

String geohash = GeoHash.withCharacterPrecision(lat, lon, 5).toBase32();
// lat/lon werden NICHT gespeichert, nur der Geohash!
```

**Frontend Decoding:** Siehe [Statistics & Heatmap](./statistics-heatmap.md#geohashing).

## Location Search (Nominatim)

**Component:** `LocationSearch.vue`

**API:** OpenStreetMap Nominatim (kostenlos, rate-limited)

**Flow:**
1. User tippt Adresse (debounced 300ms)
2. Request: `https://nominatim.openstreetmap.org/search?q={query}&format=json`
3. Response: Array of `{ display_name, lat, lon }`
4. User wählt aus Dropdown → Form wird mit lat/lon gefüllt
5. **Alternative:** User klickt "GPS nutzen" → `navigator.geolocation.getCurrentPosition()`

**Datenschutz:** Nominatim bekommt User-Input, aber **keine persistierten Daten**. Koordinaten werden nur während der Eingabe verwendet, dann zu Geohash konvertiert und verworfen.

## Odometer & Battery Level (SoC) Tracking

### Odometer
User trägt aktuellen Kilometerstand ein (optional).

### Battery Level (State of Charge)
**Seit Migration V25:** User kann den **Batteriestand nach dem Laden** erfassen (0-100%, optional).

**UI Label:** "Batteriestand nach dem Laden (%)" - verständlicher als "SoC"

**Use Case:** Präzise Verbrauchsberechnung bei Teil-Ladungen (z.B. von 30% auf 80% statt 0% auf 100%).

### Distance Calculation
Statistics-Service berechnet `distanceKm` zwischen zwei Logs mit Odometer-Daten:
```java
distanceKm = currentLog.odometerKm - previousLog.odometerKm
```

### Consumption Calculation

**Ohne SoC (nur geladene kWh):**
```java
// Problem: Funktioniert nur wenn User immer voll lädt!
consumptionKwhPer100km = (kwhCharged / distanceKm) * 100
```

**Mit SoC (präzise):**
```java
// Start-SoC berechnen aus Ende-SoC und geladenen kWh
startSoC = endSoC - (kwhCharged / batteryCapacity * 100)

// Tatsächlicher Verbrauch zwischen zwei Logs
energyConsumed = (soc1 - soc2) * batteryCapacity
consumptionKwhPer100km = (energyConsumed / distanceKm) * 100
```

**Beispiel:**
```
Log 1: 100 km, 20 kWh geladen, Ende-SoC = 80% (75 kWh Batterie)
   → Start-SoC = 80% - (20/75 × 100) = 53.3%
   → Nach Laden: 80% (= 60 kWh)

   ⬇ Fahrt: 150 km

Log 2: 250 km, 30 kWh geladen, Ende-SoC = 60%
   → Start-SoC = 60% - (30/75 × 100) = 20%
   → Vor Laden: 20% (= 15 kWh)

Verbrauch = (60 kWh - 15 kWh) / 150 km × 100 = 30 kWh/100km ✅
```

**WICHTIG:** SoC allein reicht nicht — Distanz (Odometer) ist auch erforderlich!

## Data Source Field

**Purpose:** Track wo der Log herkommt (User-Input, Import von Sprit-Monitor, Tesla API).

**Values:**
- `USER_LOGGED` (default)
- `SPRITMONITOR_IMPORT`
- `TESLA_IMPORT`
- `TESLA_FLEET` (seit Tesla Fleet API Integration)

**DB Default:** `'USER_LOGGED'` (Migration V10)

## Validation Rules

- `kwhCharged` > 0
- `costEur` >= 0
- `chargeDurationMinutes` > 0
- **`socAfterChargePercent` 0-100 (wenn gesetzt)**
- `carId` must exist and belong to user (ownership check!)
- `loggedAt` kann in der Vergangenheit liegen (aber nicht in der Zukunft)

## Security

**Ownership Checks:**
- User darf nur Logs für **eigene** Autos erstellen
- User darf nur **eigene** Logs abrufen
- JwtAuthenticationFilter extrahiert `userId` aus Token → Service prüft Ownership

**SQL Injection:** JPA Prepared Statements (safe).

## Database Migrations

**V10 (2026-03-07):** Add `soc_after_charge_percent` column
- Nullable Integer (0-100)
- Constraint: `CHECK (soc_after_charge_percent >= 0 AND soc_after_charge_percent <= 100)`
- Index: `idx_ev_log_soc` (für Queries die nach SoC filtern/sortieren)

## Known Issues

### Keine Pagination
**Problem:** `GET /api/logs?carId={uuid}` gibt **alle** Logs zurück. Bei >1000 Logs könnte das langsam werden.

**TODO:** Pagination mit `?page=0&size=50`.

### Location Search nicht gecacht
**Problem:** Jeder Keystroke macht einen Nominatim-Request (trotz 300ms Debounce).

**TODO:** Frontend-Cache für bereits gesuchte Adressen.

### SoC-basierte Verbrauchsberechnung noch nicht im Frontend
**Status:** Backend ready, Frontend zeigt noch keine SoC-basierte Consumption in Statistics.

**TODO:** Statistics-Service erweitern um SoC-basierte Consumption-Calculation.

## Related Features
- [Statistics & Heatmap](./statistics-heatmap.md) - Visualisierung
- [Car Management](./car-management.md) - CarId Selection
