# Polestar Data Portal (Testmodus)

Polestar-Fahrer verbinden ihr Fahrzeug über das Polestar Data Portal mit ev-monitor. In der Testphase werden Battery- und Odometer-Snapshots gesammelt, eine Auswertung (Ladevorgänge, Fahrten) gibt es noch nicht.

## Was der Nutzer sieht

Auf `/imports` erscheint die Sektion "Polestar Data Portal" nur, wenn ein aktives Fahrzeug der Marke Polestar in der Garage steht. Ist der Polestar das aktive Auto, startet die Sektion aufgeklappt.

1. **Im Polestar-Portal freigeben:** nummerierte Anleitung mit Link zu data-portal.polestar.com, Account-ID `polestar@ev-monitor.net` mit Kopier-Button, nur die Kategorie "Vehicle usage and status" anhaken.
2. **VIN eintragen:** ein Feld mit Validierung (17 Zeichen, kein I/O/Q), Button "Verbindung prüfen".

Ergebnisse:
- **Verbunden, Testmodus:** Status-Karte mit "Verbunden seit", "Letzter Datenempfang" und "Trennen" (mit Bestätigung).
- **Noch nicht freigegeben:** Hinweis auf Schritt 1, erneut prüfen möglich.
- **Warteliste:** alle Testplätze belegt, Freigabe im Portal kann bestehen bleiben.

Der Testmodus-Satz (Transparenz, Widerruf, Löschfrist 90 Tage) ist immer sichtbar.

## Technik

- **Connectors** (`application/polestar`): ein Third-Party-Credential für alle Nutzer (Env `POLESTAR_CLIENT_ID`, `POLESTAR_CLIENT_SECRET`, `POLESTAR_X_CLIENT_ID_HEADER`). Token wird gecacht und zwei Minuten vor Ablauf erneuert.
- **Ownership:** die VIN muss in `/v1/vehicles` unseres Credentials stehen (der Nutzer hat sie freigegeben). Eine VIN gehört genau einer Verbindung, ein Auto hat höchstens eine Verbindung.
- **Polling:** alle 5 Minuten battery und odometer für alle aktiven Verbindungen, Dedupe über `metaEventId`. 403/404 trennt die Verbindung, 429 pausiert 15 Minuten. Alle 30 Minuten Abgleich der Fahrzeugliste: fehlt eine VIN, wird die Verbindung getrennt (Freigabe entzogen).
- **Kostenwächter:** `POLESTAR_MAX_VEHICLES` (Standard 5, Free-Tier des Portals). Ab dem Limit landen neue Verbindungen auf der Warteliste.
- **Tabellen** (`ev_connectors`): `polestar_connections`, `polestar_snapshots` (Rohpayload als JSONB, Retention `POLESTAR_SNAPSHOT_RETENTION`, Standard 90 Tage).
- **Endpoints:** `/api/polestar/connections`, `/api/polestar/connect`, `DELETE /api/polestar/connections/{id}` (User-JWT, nginx und Vite leiten an den Connectors-Service). Admin: `/api/admin/polestar/summary` im Core, proxied auf `/api/internal/polestar/summary`.
- **Core:** `DataSource.POLESTAR_DATA_PORTAL` und `DataProvider.POLESTAR` sind angelegt, aber noch ohne Logs. Der Admin-Import-Tab zeigt die Verbindungs-Gesundheit als eigene Karte.
- **Löschung:** Trennen setzt die Verbindung auf DISCONNECTED und löscht alle Snapshots. Kontolöschung purgt beides.
- **Plausible:** `polestar_connect` mit `result` (success, not_shared, waitlist, error).

## Nicht enthalten

Lade- und Trip-Erkennung, Logs, Entitlement, Benachrichtigungen, Screenshots in der Anleitung.
