# Polestar Data Portal

Polestar-Fahrer verbinden ihr Fahrzeug über das Polestar Data Portal mit ev-monitor. In der Testphase werden Battery- und Odometer-Snapshots gesammelt, eine Auswertung (Ladevorgänge, Fahrten) gibt es noch nicht.

## Was der Nutzer sieht

Auf `/imports` erscheint die Sektion "Polestar Data Portal" nur, wenn ein aktives Fahrzeug der Marke Polestar in der Garage steht. Ist der Polestar das aktive Auto, startet die Sektion aufgeklappt.

1. **Im Polestar-Portal freigeben:** nummerierte Anleitung mit Link zu data-portal.polestar.com, Account-ID `polestar@ev-monitor.net` mit Kopier-Button, nur die Kategorie "Vehicle usage and status" anhaken.
2. **VIN eintragen:** ein Feld mit Validierung (17 Zeichen, kein I/O/Q), Button "Verbindung prüfen".

Ergebnisse:
- **Verbunden, Testmodus:** Status-Karte mit "Verbunden seit", "Letzter Datenempfang" und "Trennen" (mit Bestätigung).
- **Noch nicht freigegeben:** Hinweis auf Schritt 1, erneut prüfen möglich.
- **Warteliste:** alle Testplätze belegt. Der Nutzer trägt sich per Knopf ein (Opt-in, jederzeit streichbar) und wird per E-Mail informiert, sobald ein Platz frei ist. Die Freigabe im Portal kann bestehen bleiben.

Der Testmodus-Satz (Transparenz, Widerruf, Löschfrist 90 Tage) ist immer sichtbar.

## Technik

- **Connectors** (`application/polestar`): ein Third-Party-Credential für alle Nutzer (Env `POLESTAR_CLIENT_ID`, `POLESTAR_CLIENT_SECRET`, `POLESTAR_X_CLIENT_ID_HEADER`). Token wird gecacht und zwei Minuten vor Ablauf erneuert.
- **Ownership:** die VIN muss in `/v1/vehicles` unseres Credentials stehen (der Nutzer hat sie freigegeben). Eine VIN gehört genau einer Verbindung, ein Auto hat höchstens eine Verbindung.
- **Polling:** jede Minute battery und odometer (Env `POLESTAR_POLL_INTERVAL`) für alle aktiven Verbindungen, Dedupe über `metaEventId`. 403/404 trennt die Verbindung, 429 pausiert 15 Minuten. Alle 30 Minuten Abgleich der Fahrzeugliste: fehlt eine VIN, wird die Verbindung getrennt (Freigabe entzogen).
- **Kostenwächter:** `POLESTAR_MAX_VEHICLES` (Standard 5, Free-Tier des Portals). Das Portal rechnet je VIN und Kalendermonat ab, sobald wir einmal mit Datenscope zugreifen; ab der sechsten VIN zahlen alle. Gezählt werden deshalb VINs mit Zugriff im laufenden Monat (`polestar_vin_months`, Eintrag beim Verbinden und bei jedem Poll), nicht aktive Verbindungen: Trennen gibt den Platz erst am Monatsersten frei, eine im Monat schon gezählte VIN darf ohne freien Platz wieder verbinden. Ab dem Limit landen neue Verbindungen auf der Warteliste (`feature_waitlist`, Feature `POLESTAR_DATA_PORTAL`); der Admin liest sie unter `/api/admin/polestar/waitlist` und schaltet von Hand frei.
- **Tabellen** (`ev_connectors`): `polestar_connections`, `polestar_snapshots` (Rohpayload als JSONB, Retention `POLESTAR_SNAPSHOT_RETENTION`, Standard 28 Tage wie bei EUDA; so weit reicht auch das Replay), `ev_log` (erkannte Ladungen, Gegenstück zu `ev_trip`).
- **Endpoints:** `/api/polestar/connections`, `/api/polestar/connect`, `DELETE /api/polestar/connections/{id}` (User-JWT, nginx und Vite leiten an den Connectors-Service). Admin: `/api/admin/polestar/summary` im Core, proxied auf `/api/internal/polestar/summary`.
- **Core:** Fahrten und Ladungen kommen als `DataSource.POLESTAR_DATA_PORTAL` über `/api/internal/ingest`; Fahrten sind für den Besitzer immer sichtbar (keine Live-Quelle, kein Premium- und kein Modell-Gate). Der Admin-Import-Tab zeigt die Verbindungs-Gesundheit als eigene Karte.
- **Löschung:** Trennen setzt die Verbindung auf DISCONNECTED und löscht alle Snapshots. Kontolöschung purgt beides.
- **Plausible:** `polestar_connect` mit `result` (success, not_shared, waitlist, error).

## Erkennung von Fahrten und Ladungen

Der Detektor (`PolestarDetector`, Job alle zwei Minuten, Env `POLESTAR_DETECT_INTERVAL`) liest die
Snapshots jeder aktiven Verbindung und kennt kein Modell: jede Entscheidung läuft über Muster in den
Daten, ein neues Modell landet automatisch auf dem Pfad, den seine Felder hergeben. Je Verbindung
merkt sich der Job nur, bis zu welcher Fahrzeugzeit alles verarbeitet ist (`detection_cursor`).
Jede fertige Fahrt und Ladung wird zuerst als Zeile im Connector geschrieben (`ev_connectors.ev_trip`
und `ev_connectors.ev_log`, mit Detektor-Version und dem stabilen Schlüssel `end_event_id` aus den
Rohdaten), dann in zeitlicher Reihenfolge an den Core übermittelt; erst nach Erfolg rückt der Cursor vor.

**Schattenbetrieb:** Hat dasselbe Auto eine aktive Smartcar-Verbindung, erkennt der Connector weiter,
schickt aber nichts an den Core (Zeilen bleiben im Status SHADOW). Der Schatten endet von selbst, wenn
die Smartcar-Verbindung wegfällt. Der Core kennt kein Schatten-Flag; der Vergleich Schatten gegen
Smartcar läuft lesend über beide Datenbanken.

**Replay:** Nach einer Logikänderung (neue Detektor-Version) wird ein Fenster neu erkannt und per Upsert
über `end_event_id` mit den Connector-Zeilen abgeglichen: gleiche Zeile bleibt, geänderte wird ersetzt,
neue als SHADOW angelegt, verschwundene auf SUPERSEDED gesetzt. Nie löschen, nie automatisch an den Core;
bereits übermittelte Zeilen werden nur im Connector ersetzt und im Bericht ausgewiesen. Der Cursor bleibt.
Endpoint (Service-Token): `POST /api/internal/replay/connections/{id}?from=&to=&dryRun=true`, Standardfenster
die letzten 28 Tage; dryRun ist Standard und schreibt nichts.

**Fahrt**

- Ein odometer-Event gilt als bewegt, wenn der Kilometerstand oder der Trip-Meter gegenüber dem
  Vorgänger steigt. Bewegte Events bis 5 Minuten Abstand bilden eine Fahrt; ein Ladestart dazwischen
  trennt. Die Fahrt gilt 5 Minuten nach dem letzten bewegten Event als beendet.
- Ende: letztes bewegtes Event. Beginnt eine Ladung bis 5 Minuten davor, ist der Ladestart das Ende
  (das Ende-Event des Polestar 3 hängt nach).
- Startzeit, erster Treffer gewinnt: Minutenstrom (mehrere bewegte Events, Start = letztes Event
  davor), Trip-Meter-Ableitung (Ende minus Trip-Meter durch Durchschnittsgeschwindigkeit, wenn der
  Trip-Meter bis 1 km zum Odometer-Delta passt), Start-Event (unbewegtes Event seit der Vorfahrt mit
  10 bis 130 km/h Durchschnitt, nicht während einer Ladung), SoC-Abfall. Ohne Startzeit wird die
  Fahrt verworfen und protokolliert.
- Distanz: Trip-Meter des Ende-Events oder Trip-Meter-Delta, wenn genau einer bis 1 km zum
  Odometer-Delta passt, sonst Odometer-Delta. Unter 0,5 km wird nicht angelegt.
- SoC: letztes battery-Event vor dem Start, erstes Nicht-Lade-Event bis 2 Minuten nach dem Ende.
- Verbrauch, erster Treffer gewinnt: Bordzähler der Fahrt (`totalEnergyConsumptionWhAutomatic`,
  0,1 kWh, Polestar 3), Durchschnittsverbrauch mal Trip-Meter-Delta (Polestar 4), SoC-Delta mal
  effektive Kapazität ab 5 Prozentpunkten. Zähler und Ableitung müssen 5 bis 60 kWh/100 km ergeben.
  Fahrten darunter tragen Kilometer, Dauer und SoC-Paar, aber keinen Verbrauch; der Verbrauch dieser
  Autos kommt dann wie überall aus den Ladungen (`EvLogService`).
- `telemetry_extras.polestar`: Verbrauchspfad, Distanzquelle, Energieaufteilung Antrieb / Klima /
  Sonstiges / Batteriekonditionierung (nur Zählerpfad), Reichweite laut Auto am Fahrtende,
  Durchschnittsgeschwindigkeit. Kein Geohash, keine Temperatur (Standort wird nicht abgefragt).

**Ladung**

- Start: `chargingStatusV2` wechselt nach CHARGING. Ende: erstes Event mit anderem Status (IDLE,
  DONE, ERROR). Zwei Läufe werden nie zusammengefasst, ein Lauf ohne SoC-Zuwachs ist kein Ladevorgang.
- Typ aus einem CHARGING-Event, sonst aus dem Ende-Event, sonst aus der Rate (über 22 kW = DC).
- Netto-kWh = SoC-Delta mal effektive Kapazität (`energy_source = SOC_INFERRED`), kein Polestar
  liefert geladene Energie. DC gilt als öffentliche Ladung, AC bleibt offen.
- Ladekurve je Minute (`power_curve_points`: Zeit, kW, SoC, A, V), Spitzenleistung wie beobachtet,
  Endzustand im Rohdatenfeld.

**SoH:** Fahrten mit Zählerpfad speisen die SoH-Quelle `VEHICLE_COUNTER` (siehe
[Batteriegesundheit](battery-soh.md)).

## Nicht enthalten

Brutto-Schätzung aus dem Leistungsintegral, Standverlust, Entitlement, Benachrichtigungen,
Screenshots in der Anleitung.
