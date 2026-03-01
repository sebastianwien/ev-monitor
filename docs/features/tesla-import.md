# Tesla Import Integration

**Status:** ✅ Implementiert (UI nur für Tesla-Besitzer sichtbar)
**Last Updated:** 2026-03-01

## Features

- ✅ **Automatic Vehicle Wake-Up** - Backend weckt schlafende Fahrzeuge automatisch auf (max 30s Wartezeit)
- ✅ **Odometer Import** - `vehicle_state.odometer` wird importiert und von Meilen zu km konvertiert
- ✅ **Auto-Fetch Vehicle Name** - Fahrzeugname wird automatisch von Tesla API geholt
- ✅ **Token Validation** - Backend validiert Token beim Connect via Vehicle Data Fetch
- ✅ **Nullable Fields** - `cost_eur` und `geohash` können null sein (Tesla liefert keine Kosten, manchmal kein GPS)

## Overview

Automatischer Import von Ladevorgängen aus der Tesla API. User können ihren Tesla Account verbinden und Charging Data direkt aus dem Fahrzeug importieren.

**WICHTIG:** UI wird nur angezeigt wenn User mindestens einen Tesla besitzt (MODEL_3, MODEL_Y, MODEL_S, MODEL_X, CYBERTRUCK, ROADSTER).

## Domain Model

### TeslaConnection

**Entity:** `TeslaConnection.java`

**Fields:**
- `id` (UUID)
- `userId` (UUID, FK → User)
- `accessToken` (TEXT, encrypted) - Tesla OAuth Access Token (AES encrypted)
- `vehicleId` (String) - Tesla's API ID (lange Zahl, z.B. 1492931379485066)
- `vehicleName` (String) - Display name (z.B. "SKY")
- `lastSyncAt` (LocalDateTime) - Timestamp des letzten Syncs
- `autoImportEnabled` (Boolean) - Auto-Sync aktiviert? (TODO: Scheduled Job)
- `createdAt`, `updatedAt`

**Table:** `tesla_connections` (Migration V11)

**⚠️ WICHTIG - Tesla ID vs Vehicle ID:**
- **`id` (lange Zahl, z.B. 1492931379485066)** = Tesla API ID → Für Owner API Calls (`/vehicles/{id}/vehicle_data`)
- **`vehicle_id` (kurze Zahl, z.B. 1209811595)** = Legacy Vehicle Identifier → NICHT für Owner API verwenden
- User müssen die **lange `id`** aus dem `tesla_explorer.py` Script verwenden!

## Components

### Frontend

**TeslaIntegration.vue** (`frontend/src/components/TeslaIntegration.vue`)
- Verbindungs-Status anzeigen
- Token-Input Form (Access Token, Vehicle ID, Fahrzeugname)
- Manual Sync Button
- Disconnect Button
- Success/Error Messages mit Toasts

**CarManagementView.vue** (`frontend/src/views/CarManagementView.vue`)
- Rendert `<TeslaIntegration v-if="hasTesla" />`
- `hasTesla` computed property prüft ob User Tesla besitzt

### Backend

**TeslaApiService.java** (`application/tesla/TeslaApiService.java`)
- `saveConnection()` - Speichert Tesla Connection (Access Token encrypted)
- `syncChargingData()` - Fetched Vehicle Data von Tesla API, erstellt EvLog
- `getConnectionStatus()` - Returns Connection Status
- `disconnect()` - Löscht Connection

**TeslaController.java** (`infrastructure/web/TeslaController.java`)
- REST API Endpoints (siehe unten)

## User Flow

### 1. Connection Setup

1. User wählt "🔗 Tesla verbinden"
2. Form erscheint mit Anleitung:
   - Führe `tesla_explorer.py` Script aus
   - Script öffnet Browser für Tesla OAuth Login
   - Nach Login extrahiert Script automatisch:
     - **Access Token** (beginnt mit "eyJ...")
     - **Tesla ID** (lange Zahl wie 1492931379485066)
   - Kopiere beide Werte aus Console oder `~/tesla_token_*.txt`
   - Füge sie ins UI ein
3. User klickt "✅ Verbinden"
4. Backend:
   - **Fetched Vehicle Data von Tesla API** um Token zu validieren
   - Extrahiert `display_name` automatisch (z.B. "SKY")
   - Verschlüsselt Access Token (AES)
   - Speichert in `tesla_connections` Tabelle
   - Löscht alte Connection falls vorhanden (1 Connection per User)
5. Frontend zeigt Toast: "✅ Verbunden: [Fahrzeugname]"

**WICHTIG:** Fahrzeugname wird automatisch von Tesla API geholt, kein manueller Input nötig!

### 2. Manual Sync

1. User klickt "⚡ Jetzt synchronisieren"
2. Backend:
   - Fetched Vehicle Data von Tesla API: `https://owner-api.teslamotors.com/api/1/vehicles/{vehicleId}/vehicle_data`
   - Prüft ob Charging Data vorhanden: `chargeState.chargeEnergyAdded > 0.1`
   - Prüft Duplikate: `existsByCarIdAndLoggedAtBetween(±1h)`
   - Erstellt EvLog mit `data_source = TESLA_IMPORT`
3. Frontend:
   - Toast: "🎉 Sync erfolgreich! X Ladevorgang(e) importiert • Batteriestand: Y%"

### 3. Disconnect

1. User klickt "Trennen"
2. Confirmation Dialog: "Tesla Account wirklich trennen?"
3. Backend löscht Connection (inkl. verschlüsselter Access Token)

## API Endpoints

### POST /api/tesla/connect
**Headers:** `Authorization: Bearer {jwt}`

**Request:**
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIs...",
  "vehicleId": "1492931379485066",
  "vehicleName": "Tesla"
}
```
Note: `vehicleId` ist die **lange Tesla API ID**, nicht die kurze `vehicle_id`!

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Tesla account connected successfully",
  "vehicleName": "SKY"
}
```

**Error (400 Bad Request):**
```json
{
  "success": false,
  "message": "Failed to connect: Invalid access token",
  "vehicleName": null
}
```

### POST /api/tesla/sync
**Headers:** `Authorization: Bearer {jwt}`

**Response (200 OK):**
```json
{
  "logsImported": 1,
  "vehicleName": "SKY",
  "batteryLevel": 85
}
```

**Error (400 Bad Request):** Wenn keine Connection existiert oder Tesla API fehlschlägt.

### GET /api/tesla/status
**Headers:** `Authorization: Bearer {jwt}`

**Response (200 OK - Connected):**
```json
{
  "connected": true,
  "vehicleName": "SKY",
  "lastSyncAt": "2025-03-01T10:30:00",
  "autoImportEnabled": false
}
```

**Response (200 OK - Not Connected):**
```json
{
  "connected": false,
  "vehicleName": null,
  "lastSyncAt": null,
  "autoImportEnabled": false
}
```

### DELETE /api/tesla/disconnect
**Headers:** `Authorization: Bearer {jwt}`

**Response (204 No Content)**

## Tesla API Integration

### API Base URL
`https://owner-api.teslamotors.com/api/1`

### Endpoint Used
`GET /vehicles/{vehicleId}/vehicle_data`

**Response Structure:**
```json
{
  "response": {
    "id": 1209811595,
    "display_name": "SKY",
    "vin": "5YJ3E1EA...",
    "charge_state": {
      "timestamp": 1709271743000,
      "battery_level": 85,
      "charge_energy_added": 45.5,
      "charger_power": 50
    },
    "drive_state": {
      "latitude": 52.520008,
      "longitude": 13.404954,
      "native_latitude": 52.520008,
      "native_longitude": 13.404954
    },
    "vehicle_config": {
      "car_type": "model3",
      "trim_badging": "74"
    }
  }
}
```

**Mapping zu EvLog:**
- `charge_energy_added` → `kwhCharged`
- `charger_power` → `maxChargingPowerKw`
- `timestamp` → `loggedAt` (Unix millis → LocalDateTime)
- `latitude/longitude` → Geohash (5 chars, ~5km) - `null` wenn GPS nicht verfügbar
- `vehicle_state.odometer` → `odometerKm` ✅ **NEU: Wird importiert + konvertiert (Meilen → km)**
- `costEur` → `null` (User muss manuell nachtragen) ✅ **NEU: Nullable seit Migration V12**
- `chargeDurationMinutes` → `null` (nicht verfügbar in API)
- `data_source` → `"TESLA_IMPORT"`

**Odometer Konvertierung:**
- Tesla API gibt Odometer in **Meilen** zurück
- Backend konvertiert automatisch: `miles * 1.609344 = km`
- Beispiel: `38016.23 miles` → `61187 km`

## Security

### Access Token Encryption

**Algorithm:** AES (128-bit)

**Key Source:** `application.yml` → `tesla.encryption.key`
- **Dev:** `change-this-32-char-secret-key!!`
- **Prod:** Environment Variable (in `.env` auf Server)

**Implementation:**
```java
// Encryption (TeslaApiService.java:254)
SecretKeySpec keySpec = new SecretKeySpec(
    encryptionKey.substring(0, 16).getBytes(StandardCharsets.UTF_8),
    ALGORITHM
);
Cipher cipher = Cipher.getInstance(ALGORITHM);
cipher.init(Cipher.ENCRYPT_MODE, keySpec);
byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
return Base64.getEncoder().encodeToString(encrypted);
```

**Decryption:** Analog mit `Cipher.DECRYPT_MODE`.

**WICHTIG:**
- Access Token wird **NIEMALS** im Klartext gespeichert
- Nur verschlüsselt in DB (`tesla_connections.access_token`)
- Decryption nur für API Calls (in-memory)

### Token Refresh (TODO)

**Problem:** Tesla Access Tokens expiren nach ~45 Tagen.

**TODO:**
- Refresh Token speichern + verschlüsseln
- Auto-Refresh bei Sync wenn Token expired
- Error Handling: User muss neu verbinden falls Refresh fehlschlägt

## Auto-Import (TODO)

**Field:** `tesla_connections.auto_import_enabled`

**Planned Feature:**
- Scheduled Job (täglich) fetched automatisch neue Logs
- User kann Auto-Import in UI aktivieren/deaktivieren
- Benachrichtigung bei neuen Logs?

**Status:** Infrastructure vorhanden, aber **nicht implementiert**.

## Car Auto-Creation

**Logic:** `TeslaApiService.findOrCreateCar()` (Zeile 162)

**Wenn kein Tesla Car existiert:**
- Erstellt automatisch neues Car basierend auf Tesla API Data
- Mapping:
  - `vehicle_config.car_type` → `CarBrand.CarModel` (MODEL_3, MODEL_S, etc.)
  - `vin` → Extrahiert Baujahr (simplified, aktuell Jahr - 2)
  - `display_name` → `license_plate` (User kann später ändern)
  - `vehicle_config.trim_badging` → `battery_capacity_kwh` (z.B. "74" → 74 kWh)

**Wenn Tesla Car existiert:**
- Findet existierendes Car via:
  - Model Name Match (MODEL_3, MODEL_S, etc.)
  - ODER License Plate Match (falls `display_name` in Kennzeichen)

## Duplicate Prevention

**Logic:** `TeslaApiService.syncChargingData()` (Zeile 88)

```java
boolean alreadyExists = evLogRepository.existsByCarIdAndLoggedAtBetween(
    car.getId(),
    loggedAt.minusHours(1),
    loggedAt.plusHours(1)
);
```

**Time Window:** ±1 Stunde um `loggedAt`

**Why?** Tesla API liefert manchmal gleiche Charging Data bei mehreren Syncs.

## Limitations

- **Manual OAuth Flow** - User muss `tesla_explorer.py` Script ausführen für Token-Generierung (TODO: OAuth im UI)
- **Incomplete Data** - Tesla API liefert keine Kosten/Ladedauer → User muss manuell nachtragen
- **Excluded from Statistics** - Tesla Import Logs werden bei Berechnungen ignoriert (`WHERE data_source != 'TESLA_IMPORT'`)
- **No Token Refresh** - Access Tokens expiren nach ~45 Tagen, User muss neu verbinden (TODO: Refresh Token)
- **Manual Sync Only** - Kein automatischer Import (TODO: Scheduled Job)
- **Single Vehicle** - Nur ein Tesla pro User (TODO: Multi-Vehicle Support)
- **Simplified VIN Parsing** - Baujahr wird geschätzt statt aus VIN extrahiert

## Statistics Exclusion

**WICHTIG:** Tesla Import Logs werden von folgenden Berechnungen ausgeschlossen:

✅ **Ausgeschlossen:**
- User Statistics (Durchschnittspreis, Ladedauer, Kosten)
- Public Model Stats (Community-Durchschnitte)
- WLTP-Vergleiche (Consumption Calculations)

✅ **Eingeschlossen:**
- Dashboard Log-Liste (User sieht alle eigenen Logs)
- Heatmap (nur Location-Visualisierung, keine Berechnungen)

**Grund:** Incomplete data (`cost_eur` und `charge_duration_minutes` sind NULL) würde Statistiken verfälschen.

## UI Visibility Rule

**Logic:** `CarManagementView.vue` (Zeile 56-60)

```typescript
const hasTesla = computed(() => {
  const teslaModels = ['MODEL_3', 'MODEL_Y', 'MODEL_S', 'MODEL_X', 'CYBERTRUCK', 'ROADSTER']
  return cars.value.some(car => teslaModels.includes(car.model))
})
```

**Template:** `<TeslaIntegration v-if="hasTesla" />`

**Why?** Kein Tesla → Keine Integration anzeigen (UI cleaner).

## Related Features
- [Charging Logs](./charging-logs.md) - EvLog mit `data_source = TESLA_IMPORT`
- [Car Management](./car-management.md) - Auto-Creation von Tesla Cars
- [Authentication](./authentication.md) - JWT für API Calls

## Testing

**Manual Test Flow:**
1. Erstelle Tesla Car in UI (z.B. MODEL_3)
2. Tesla Integration erscheint unten auf `/cars`
3. Führe `tesla_explorer.py` aus (extern)
4. Kopiere Access Token + **Tesla ID** (lange Zahl!) ins UI
5. Klick "Verbinden"
6. Klick "Jetzt synchronisieren"
7. Toast: "🎉 Sync erfolgreich! 1 Ladevorgang importiert"
8. Gehe zu `/dashboard` → Neuer Log mit `data_source = TESLA_IMPORT`

**WICHTIG:** Nutze die **lange Tesla ID** (z.B. 1492931379485066), NICHT die kurze vehicle_id!

**Mock Access Token (für Dev):**
- TODO: Mock Tesla API für Testing ohne echte Tesla Credentials

## Environment Variables

**Backend (`application.yml`):**
```yaml
tesla:
  encryption:
    key: ${TESLA_ENCRYPTION_KEY:change-this-32-char-secret-key!!}
```

**Prod (`.env` auf Hetzner):**
```bash
TESLA_ENCRYPTION_KEY=<32-char-secret-key>
```

**WICHTIG:** Encryption Key **MUSS** min. 16 Zeichen haben (AES-128)!
