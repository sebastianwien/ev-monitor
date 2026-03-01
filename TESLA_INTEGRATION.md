# 🚗 Tesla Integration Guide

## Übersicht

Die Tesla-Integration ermöglicht automatischen Import von Ladevorgängen aus deinem Tesla Account.

## Setup (für User)

### 1. Python Script ausführen

```bash
cd /Users/sebastian.wien/private/ev-monitor
python3 tesla_explorer.py
```

Das Script:
- Fragt nach deiner Tesla-Email
- Öffnet Browser für Login
- Holt alle Vehicle-Daten
- Speichert JSON im Home-Verzeichnis

### 2. Daten aus JSON extrahieren

Nach dem Script-Lauf brauchst du:

```json
{
  "basic_info": {
    "vehicle_id": 1209811595,     // <-- DAS!
    "display_name": "SKY"          // <-- Optional
  }
}
```

**Access Token** ist schwieriger - das Script speichert ihn lokal in:
```
~/.cache/teslapy/cache.json
```

Oder du nutzt `teslapy` direkt:
```python
import teslapy
tesla = teslapy.Tesla('deine@email.com')
tesla.fetch_token()
print(tesla.token['access_token'])  # <-- DIESER Token!
```

### 3. In EV Monitor verbinden

1. Gehe zu "Fahrzeuge verwalten"
2. Scrolle runter zu "Tesla Integration"
3. Klicke "Tesla verbinden"
4. Gib ein:
   - Access Token (von oben)
   - Vehicle ID (z.B. 1209811595)
   - Fahrzeugname (optional, z.B. "SKY")
5. Klicke "Verbinden"

### 4. Ladevorgänge synchronisieren

- Klicke auf "Jetzt synchronisieren"
- Das System holt die aktuellen Daten und erstellt automatisch EvLog-Einträge

## Technische Details

### Backend

**Endpoints:**
- `POST /api/tesla/connect` - Tesla Account verbinden
- `GET /api/tesla/status` - Connection-Status abrufen
- `POST /api/tesla/sync` - Manueller Sync
- `DELETE /api/tesla/disconnect` - Verbindung trennen

**Security:**
- Access Token wird **AES-verschlüsselt** in DB gespeichert
- Encryption Key muss in `.env` gesetzt sein: `TESLA_ENCRYPTION_KEY`
- User kann nur eigene Connection sehen/ändern

**Daten-Mapping:**
| Tesla API | EV Monitor |
|-----------|------------|
| `charge_energy_added` | `kwhCharged` |
| `native_latitude/longitude` | `geohash` (5 chars) |
| `timestamp` | `loggedAt` |
| ❌ | `costEur` (bleibt null, User muss manuell setzen) |

### Frontend

**Component:** `TeslaIntegration.vue`
- Zeigt Connection-Status
- Form für Token-Eingabe
- Sync-Button
- Success/Error Messages

**Service:** `teslaService.ts`
- API Calls via Axios
- TypeScript Types

## Bekannte Limitationen (MVP)

- ✅ Manueller Sync (kein Auto-Import im Background)
- ✅ Keine Kosten-Daten (Tesla API hat keine Pricing-Infos)
- ✅ Nur letzte Charging-Session (keine History)
- ✅ Kein Refresh Token (Access Token läuft nach ~8h ab, dann neu verbinden)
- ✅ Deduplizierung via Timestamp (±1h Window)

## Nächste Schritte (Post-MVP)

1. **Scheduled Background Sync** - Stündlicher Job holt automatisch neue Daten
2. **Refresh Token Support** - Auto-Renewal wenn Token abläuft
3. **Charging History** - Letzte 7 Tage importieren (nicht nur aktueller Stand)
4. **Live-Status** - Zeige "🔌 Lädt gerade: 11 kW" im Dashboard
5. **Kosten-Schätzung** - Basierend auf Standort & öffentlichen Preisen

## Troubleshooting

**"Failed to connect":**
- Access Token abgelaufen? → Neu generieren mit Script
- Vehicle ID falsch? → Check JSON aus tesla_explorer.py
- TESLA_ENCRYPTION_KEY nicht gesetzt? → Check .env

**"No logs imported":**
- Auto war seit letztem Sync nicht laden? → Normal!
- `charge_energy_added` = 0? → Wird nicht importiert (zu wenig Daten)

**"Token invalid":**
- Access Token läuft nach ~8h ab
- Einfach neu verbinden mit frischem Token

## Sicherheit & Privacy

✅ **Token Encryption** - AES-256 in DB
✅ **GPS → Geohash** - Nur ~5km Präzision gespeichert
✅ **User Isolation** - Jeder sieht nur eigene Connection
✅ **No Plaintext** - Kein Token in Logs/Responses

## Support

Bei Problemen check:
1. Backend Logs: `docker compose logs -f backend`
2. Browser Console (F12)
3. Database: `SELECT * FROM tesla_connections WHERE user_id = '...'`
