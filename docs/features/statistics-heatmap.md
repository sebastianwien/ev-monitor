# Statistics & Heatmap Feature

**Status:** ✅ Implementiert
**Last Updated:** 2026-03-01

## Overview

Zeigt aggregierte Statistiken und geografische Übersicht der Ladevorgänge eines Fahrzeugs.

## Components

### Frontend
- **StatisticsView.vue** (`frontend/src/views/StatisticsView.vue`)
  - Hauptseite mit Filters (Zeitraum, Gruppierung)
  - Chart.js Line Charts (Kosten, kWh, Verbrauch über Zeit)
  - WLTP Delta Bar Chart (Vergleich Real vs. WLTP)
  - Integriert ChargingHeatMap Komponente

- **ChargingHeatMap.vue** (`frontend/src/components/ChargingHeatMap.vue`)
  - Leaflet.js Map mit OpenStreetMap Tiles
  - Heatmap Layer (leaflet.heat) - Intensität nach kWh
  - Marker Cluster Layer (leaflet.markercluster)
  - View Mode Toggle: Heatmap | Markers | Both
  - Popups: kWh, Kosten, Dauer, Datum

### Backend
- **EvLogController.java**
  - `GET /api/logs/statistics?carId={uuid}&timeRange={enum}&groupBy={enum}`
  - Returns: `EvLogStatisticsResponse` (aggregierte Daten)

- **EvLogService.java**
  - `getStatistics(UUID carId, TimeRange timeRange, GroupBy groupBy)`
  - Aggregiert Logs nach Zeitraum und Gruppierung
  - Berechnet: totalKwh, avgCost, totalDistance, avgConsumption

## API Endpoints

### GET /api/logs/statistics
**Query Params:**
- `carId` (UUID, required) - Vehicle ID
- `timeRange` (enum, required) - THIS_MONTH | LAST_MONTH | LAST_3_MONTHS | LAST_6_MONTHS | LAST_12_MONTHS | THIS_YEAR | ALL_TIME
- `groupBy` (enum, required) - DAY | WEEK | MONTH

**Response:**
```json
{
  "totalKwhCharged": 1234.5,
  "totalCostEur": 450.20,
  "avgCostPerKwh": 0.36,
  "cheapestChargeEur": 5.20,
  "mostExpensiveChargeEur": 85.40,
  "avgChargeDurationMinutes": 45,
  "totalCharges": 120,
  "totalDistanceKm": 8500,
  "avgConsumptionKwhPer100km": 17.2,
  "chargesOverTime": [
    {
      "timestamp": "2025-12-01T00:00:00",
      "costEur": 111.23,
      "kwhCharged": 270.84,
      "distanceKm": 1269,
      "consumptionKwhPer100km": 21.34
    }
  ]
}
```

### GET /api/logs?carId={uuid}
**Query Params:**
- `carId` (UUID, required) - Vehicle ID

**Response:** Array of `EvLogResponse`
```json
[
  {
    "id": "uuid",
    "carId": "uuid",
    "kwhCharged": 45.44,
    "costEur": 15.50,
    "chargeDurationMinutes": 62,
    "geohash": "u33c9",
    "odometerKm": 22743,
    "maxChargingPowerKw": 50.0,
    "loggedAt": "2025-03-01T10:30:00",
    "createdAt": "2025-03-01T10:45:00",
    "updatedAt": "2025-03-01T10:45:00"
  }
]
```

## Geohashing

**Privacy-First Approach:**
- GPS-Koordinaten werden **NIEMALS** in der DB gespeichert
- Frontend sendet `lat/lon` → Backend konvertiert zu **5-char Geohash** (ch.hsr.geohash)
- Präzision: ~5km Radius
- Beispiel: `u33d1` = ~5km um Berlin Mitte

**Frontend Decoding:**
- npm package: `ngeohash`
- `geohash.decode("u33c9")` → `{ latitude: 52.52, longitude: 13.405 }`

## Known Issues & Fixes

### Issue: Heatmap zeigt keine Marker (Fixed 2026-03-01)
**Problem:** ChargingHeatMap.vue rendered das `mapContainer` DIV nur wenn `chargeCount > 0`. Beim Mount war `chargeCount = 0` → DIV existierte nicht → Leaflet Map konnte nicht initialisiert werden → Henne-Ei-Problem.

**Lösung:**
- `mapContainer` DIV wird **immer** gerendert
- "Keine Daten"-Message als absolutes Overlay darüber (`position: absolute`)
- Map kann sofort beim Mount initialisiert werden

### Issue: Vite Proxy fehlte
**Problem:** Frontend auf Port 5173 machte Requests an sich selbst statt ans Backend (Port 8080).

**Lösung:** `.env.local` mit `VITE_API_BASE_URL=http://localhost:8080/api` (bereits vorhanden, Vite Neustart nötig)

## Dependencies

**Frontend:**
- `leaflet` - Map Library
- `leaflet.heat` - Heatmap Layer
- `leaflet.markercluster` - Marker Clustering
- `ngeohash` - Geohash Decoding
- `chart.js` + `vue-chartjs` - Charts

**Backend:**
- `ch.hsr:geohash` - Geohash Encoding (in Gradle dependencies)

## Related Features
- [Charging Logs](./charging-logs.md) - Datenquelle für Statistics
- [WLTP Crowdsourcing](./wltp-crowdsourcing.md) - WLTP Delta Chart
- [Car Management](./car-management.md) - Car Selection
