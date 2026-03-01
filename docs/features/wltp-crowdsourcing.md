# WLTP Crowdsourcing & Coin System

**Status:** ✅ Implementiert (Coin-Rewards teilweise)
**Last Updated:** 2026-03-01

## Overview

Community-basierte Datenbank für offizielle WLTP-Werte (Reichweite, Verbrauch) von E-Autos. User können Daten beitragen und erhalten Coins als Belohnung.

## Domain Model

### VehicleSpecification

**Fields:**
- `id` (UUID)
- `carBrand` (String) - CarBrand enum name (z.B. "TESLA")
- `carModel` (String) - CarModel enum name (z.B. "MODEL_3")
- `batteryCapacityKwh` (BigDecimal)
- `wltpRangeKm` (BigDecimal) - Offizielle WLTP Reichweite
- `wltpConsumptionKwhPer100km` (BigDecimal) - Offizielle Verbrauchswerte
- `wltpType` (String) - `COMBINED` | `HIGHWAY` | `CITY`
- `createdAt`, `updatedAt`

**UNIQUE Constraint:** `(car_brand, car_model, battery_capacity_kwh, wltp_type)`

### CoinLog

**Fields:**
- `id` (UUID)
- `userId` (UUID, FK → User)
- `amount` (Integer) - Coin-Menge (z.B. 50)
- `coinType` (String) - `GREEN_COIN` | `DISTANCE_COIN` | `SOCIAL_COIN` | `STREAK_COIN` | `ACHIEVEMENT_COIN` | `EFFICIENCY_COIN`
- `actionDescription` (String) - z.B. "WLTP data contribution for Tesla Model 3"
- `createdAt`

## WLTP Crowdsourcing Flow

### Frontend (CarManagementView.vue)

1. **User wählt Auto:** Brand → Model → Battery Capacity
2. **Automatischer Lookup:** `vehicleSpecificationService.lookup(brand, model, capacity)`
   - **Daten vorhanden** → Blauer Info-Banner: "📊 WLTP: 450km Reichweite, 16.5 kWh/100km"
   - **Keine Daten (404)** → **Overlay 1** öffnet sich automatisch
3. **Overlay 1 (Frage):** "Wir haben noch keine WLTP-Werte. Möchtest du diese angeben?"
   - ✅ **Ja (grün)** → Overlay 2 öffnet sich
   - ❌ **Nein (rot)** → Overlay schließt, keine Aktion
4. **Overlay 2 (Form):** Input für Range (km) + Consumption (kWh/100km)
   - Validation: Beide Felder required, Zahlen mit Dezimalstellen
   - Submit → `POST /api/vehicle-specifications`
5. **Toast Notification:** "🎉 Danke! 50 Coins erhalten! Die Community profitiert von deinen Daten."
   - Auto-fade nach 5 Sekunden, Slide-in Animation von rechts

### Backend (VehicleSpecificationService.java)

1. **Prüft Duplikate:** UNIQUE constraint (brand, model, capacity, type)
2. **Speichert Daten** in `vehicle_specification` Tabelle
3. **Vergibt 50 SOCIAL_COIN:** `CoinLogService.awardCoins(userId, 50, SOCIAL_COIN, "WLTP data contribution for {brand} {model}")`
4. **Returns 201 Created** mit neuer VehicleSpecification

## API Endpoints

### GET /api/vehicle-specifications/lookup
**Query Params:**
- `brand` (String, required) - CarBrand enum name
- `model` (String, required) - CarModel enum name
- `capacityKwh` (BigDecimal, required)

**Response (200 OK):**
```json
{
  "id": "uuid",
  "carBrand": "TESLA",
  "carModel": "MODEL_3",
  "batteryCapacityKwh": 75.0,
  "wltpRangeKm": 450.0,
  "wltpConsumptionKwhPer100km": 16.5,
  "wltpType": "COMBINED",
  "createdAt": "2025-03-01T10:00:00",
  "updatedAt": "2025-03-01T10:00:00"
}
```

**Response (404 Not Found):** Keine Daten vorhanden (Frontend interpretiert als "bitte beitragen")

### POST /api/vehicle-specifications
**Headers:** `Authorization: Bearer {token}`

**Request:**
```json
{
  "carBrand": "TESLA",
  "carModel": "MODEL_3",
  "batteryCapacityKwh": 75.0,
  "wltpRangeKm": 450.0,
  "wltpConsumptionKwhPer100km": 16.5,
  "wltpType": "COMBINED"
}
```

**Response (201 Created):** VehicleSpecification + 50 Coins awarded

**Error (409 Conflict - Duplicate):**
```json
{
  "error": "DUPLICATE_SPECIFICATION",
  "message": "WLTP data already exists for this vehicle configuration"
}
```

## Coin System

### Infrastructure
- ✅ `CoinLog` Entity + Repository
- ✅ `CoinLogService` mit `awardCoins()` Methode
- ✅ `CoinLogController` mit `/api/coins/balance` + `/api/coins/history`

### Implemented Rewards
- ✅ **WLTP Data Contribution:** 50 SOCIAL_COIN (bei neuem WLTP-Datensatz)

### TODO: Weitere Rewards
- ❌ **EvLog Creation:** 5 GREEN_COIN pro Log
- ❌ **Streak Rewards:** 10 STREAK_COIN bei 7 Tagen in Folge geloggt
- ❌ **Milestone Rewards:** 100 ACHIEVEMENT_COIN bei 100. Log
- ❌ **Profile Completion:** 25 SOCIAL_COIN bei vollständigem Profil
- ❌ **Efficiency Bonus:** 20 EFFICIENCY_COIN bei <15 kWh/100km über 1 Monat

### Coin Types Explained
- **GREEN_COIN:** Umwelt-Belohnung (pro Log)
- **DISTANCE_COIN:** Distanz-Meilensteine
- **SOCIAL_COIN:** Community-Beiträge (WLTP, Profilbild, Bio)
- **STREAK_COIN:** Konsistenz-Belohnung (tägliches Logging)
- **ACHIEVEMENT_COIN:** Große Meilensteine (100 Logs, 10.000 km)
- **EFFICIENCY_COIN:** Effizienz-Bonus (niedriger Verbrauch)

## WLTP Delta Chart (Statistics)

**Component:** `StatisticsView.vue` (Bar Chart)

**Calculation:**
```javascript
delta = realConsumption - wltpConsumption
```

**Colors:**
- 🟢 Grün: Effizienter als WLTP (delta < 0)
- 🔴 Rot: Mehr Verbrauch als WLTP (delta > 0)

**Display:** Bar Chart mit Delta in kWh/100km, Tooltip zeigt WLTP-Referenz.

## Seed Data

**Migration:** `V8__seed_wltp_data.sql`

**Status:** Leer (Community muss befüllen)

**TODO:** Seed mit populären Modellen:
- Tesla Model 3 (57.5, 75.0, 79.0 kWh)
- VW ID.3 (58.0, 77.0 kWh)
- Hyundai Ioniq 5 (58.0, 77.4 kWh)
- BMW i4 (80.7 kWh)

## Known Issues

### WLTP-Type ist immer COMBINED
**Problem:** Frontend sendet hardcoded `COMBINED`. Highway/City WLTP-Werte nicht implementiert.

**TODO:** Dropdown im Overlay 2 für WLTP-Type Selection.

### Keine WLTP-Quelle
**Problem:** Keine Verlinkung zur offiziellen WLTP-Quelle (z.B. DAT, ADAC).

**TODO:** Optional `sourceUrl` Feld für Quellenangabe.

### Keine Moderation
**Problem:** User können falsche Daten eintragen, keine Validierung gegen offizielle Werte.

**TODO:** Admin-Review oder Community-Voting System.

## Related Features
- [Statistics & Heatmap](./statistics-heatmap.md) - WLTP Delta Chart
- [Car Management](./car-management.md) - WLTP Lookup beim Auto-Erstellen
