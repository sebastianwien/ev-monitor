# Verbrauchsberechnung

**Status:** ✅ Implementiert
**Letzte Änderung:** 2026-03-10

## Überblick

Die Verbrauchsberechnung ist zentralisiert in `EvLogService`. **Alle** Verbrauchswerte in der App — Dashboard, User-Statistiken, Community-Modellseiten, saisonale Aufschlüsselungen — verwenden dieselbe Logik. Es gibt keine eigene SQL-Formel oder parallele Implementierung.

**Einzige autoritative Quelle:** `EvLogService.java`
**Konfiguration:** `PlausibilityProperties.java` (konfigurierbar via `application.yml`)

---

## Kernformel: SoC-basierter Verbrauch

```
socBeforeLogY     = socAfterLogY - (kwhCharged / batteryCapacity × 100)
energyConsumedKwh = (socAfterLogX - socBeforeLogY) × batteryCapacity / 100
consumption       = energyConsumedKwh / distanceKm × 100   [kWh/100km]
```

- **logX** = vorheriger Ladevorgang (Startpunkt der Strecke)
- **logY** = aktueller Ladevorgang (Endpunkt der Strecke)
- Misst echten Energieverbrauch aus der Batterie — nicht nur was geladen wurde
- Erfordert `odometerKm` + `socAfterChargePercent` an logX, und zusätzlich `kwhCharged` an logY

### Log-Anforderungen

| Rolle | Felder | Methode |
|-------|--------|---------|
| logX (Startpunkt) | `odometerKm` + `socAfterChargePercent` | `canBeUsedAsLogX()` |
| logY (Endpunkt) | `odometerKm` + `kwhCharged` + `socAfterChargePercent` | `isComplete()` |

### Strict-by-Design: Keine Lücken überspringen

`findPreviousLog()` gibt den nächsten als logX verwendbaren Vorgänger zurück. Ein Zwischenlog, der nicht als logX verwendbar ist (kein Odometer / kein SoC), bricht die Kette und der Trip wird verworfen - denn er repräsentiert im Allgemeinen ein unbekanntes Energieereignis, das die SoC-Delta-Berechnung korrumpieren würde.

**Ausnahme - transparente Zwischenladungen:** Zwei Kategorien von Logs sind für die Kette transparent, weil ihre Energie vollständig bekannt ist. Sie werden übersprungen und ihre kWh als `intermediateKwh` zur Energiebilanz addiert (rollierendes Fenster zwischen zwei Ankern mit Odometer + SoC):

| Kategorie | Bedingung | kWh-Beitrag |
|---|---|---|
| go-e Sub-Sessions | `dataSource = WALLBOX_GOE` | rohe `kwhCharged` |
| Spritmonitor-Teilladungen | `dataSource = SPRITMONITOR_IMPORT` + kein Odometer + `kwhCharged > 0` | fahrzeugseitig normalisiert (× Ladeeffizienz) |

Die Spritmonitor-Regel ist bewusst eng gefasst: Spritmonitor-Nutzer pflegen bei Teilladungen häufig keinen Kilometerstand, die geladene Energie ist aber exakt erfasst. Eine Spritmonitor-Teilladung **ohne** kWh bricht die Kette weiterhin (Energie unbekannt). Andere Quellen ohne Odometer (z.B. `TESLA_FLEET_IMPORT` bei Snapshot-Miss, manuelle Logs) brechen die Kette ebenfalls weiterhin - dort kann ein fehlender Odometer auf fehlende Sessions hindeuten.

Übersprungene Teilladungen erhalten in der API das Flag `kwhCountedInNextConsumption`, damit die UI erklären kann, warum sie keinen eigenen Verbrauchswert zeigen.

---

## Berechnung pro Log: `calculateConsumptionPerLog()`

Zwei-Pass-Algorithmus, der für jeden Log einen Verbrauchswert + Plausibilitätsverdikt liefert.

**Pass 1 — Rohwerte berechnen:**
- Filtert auf `isComplete()` Logs
- Findet für jeden logY den unmittelbar vorherigen logX via `findPreviousLog()`
- Verwirft Trips kürzer als `minTripDistanceKm` (Standard: 10 km) — zu kurze Trips haben unzuverlässige Odometer-Diffs
- Berechnet `calculateConsumption(logX, logY, batteryCapacity)` → kWh/100km

**Pass 2 — Plausibilitätsprüfung:**
- Ruft `isConsumptionPlausible()` für jeden Rohwert auf
- Ergebnis: `Map<logId → ConsumptionResult(value, plausible, distanceKm)>`

---

## Plausibilitätsprüfung: `isConsumptionPlausible()`

Drei-Layer-Check — alle Layer werden der Reihe nach geprüft:

### Layer 1: Absolute Grenzen (immer aktiv)
```
absoluteMinKwhPer100km = 10.0  →  Untergrenze (physikalisch unmöglich darunter)
absoluteMaxKwhPer100km = 40.0  →  Obergrenze (auch Heizung + Autobahn bei E-Auto)
```

### Layer 2a: Statistischer Check (wenn ≥ `minTripsForStatistical` Trips vorhanden)
```
plausibel wenn: mean(history) - σ×sigmaMultiplier ≤ wert ≤ mean(history) + σ×sigmaMultiplier
sigmaMultiplier = 2.5   →  99.4% einer Normalverteilung werden akzeptiert
minTripsForStatistical = 5
```

### Layer 2b: WLTP-Bootstrap (wenn < 5 Trips, aber WLTP-Wert bekannt)
```
plausibel wenn: WLTP × wltpLowerFactor ≤ wert ≤ WLTP × wltpUpperFactor
wltpLowerFactor = 0.75   →  25% besser als WLTP noch akzeptiert
wltpUpperFactor = 2.2    →  120% schlechter als WLTP noch akzeptiert
```

### Layer 2c: Nur absolute Grenzen (keine History, kein WLTP)

---

## Fallback: `calculateConsumptionFallback()`

Wird verwendet wenn kein Log SoC-Daten hat (z.B. reine Sprit-Monitor-Imports):

```
consumption = Σ(kwhCharged) / totalDistanceKm × 100
```

Weniger präzise als SoC-basiert (misst was geladen wurde, nicht was verbraucht wurde). Vor dem Fallback werden Logs mit unplausiblem kWh/km-Verhältnis gefiltert (Layer 1 Grenzen).

---

## Community-Durchschnittsverbrauch: `calculateCommunityAvgConsumption()`

Berechnet den Gesamtdurchschnitt für alle Autos eines Modells. Wird in der **Public Model Stats API** verwendet.

**Ablauf pro Fahrzeug:**
1. Alle Logs laden (chronologisch sortiert)
2. Filtern nach `includeInStatistics` (außer `isSeedUser=true` → alle Logs)
3. SoC-Pfad: `calculateConsumptionPerLog()` → plausible Ergebnisse sammeln
4. Fallback-Pfad (wenn kein einziges SoC-Ergebnis): `calculateConsumptionFallback()` mit absolutem Plausibilitätsfilter

**Aggregation (distanzgewichtet):**
```
carConsumption   = Σ(cr.value × cr.distanceKm) / Σ(cr.distanceKm)   [pro Fahrzeug]
communityResult  = Σ(carConsumption × carDistanceKm) / Σ(carDistanceKm)   [über alle Fahrzeuge]
```

Autos mit mehr gefahrenen Kilometern haben mehr Gewicht als Autos mit einzelnen Logs.

---

## Saisonale Aufschlüsselung: `calculateSeasonalConsumption()`

Identische Logik wie `calculateCommunityAvgConsumption()`, aber mit Bucketing nach Jahreszeit:

| Jahreszeit | Monate |
|------------|--------|
| Sommer | April – September (Monat 4–9) |
| Winter | Oktober – März (Monat 1–3, 10–12) |

Für jeden plausiblen Log wird der Monat von `loggedAt` geprüft und der Log dem jeweiligen Sommer- oder Winter-Akkumulator zugeordnet.

**Rückgabe: `SeasonalConsumptionResult`**
- `summerConsumptionKwhPer100km` — distanzgewichteter Ø Sommer (nullable)
- `winterConsumptionKwhPer100km` — distanzgewichteter Ø Winter (nullable)
- `totalConsumptionKwhPer100km` — distanzgewichteter Gesamtwert aus `(summerWeighted + winterWeighted) / (summerKm + winterKm)` — konsistent mit den Saisonwerten
- `summerKm`, `winterKm` — gefahrene km pro Jahreszeit (für Prozentsatz-Berechnung)
- `summerLogCount`, `winterLogCount` — Anzahl beitragender Logs

Der **Gesamtwert wird im Backend berechnet**, nicht im Frontend aus gerundeten Prozentsätzen — das verhindert Rundungsfehler.

---

## Verbrauch je Streckentyp: `calculateRouteTypeConsumption()`

Liefert den Community-Verbrauch getrennt nach Stadt (`CITY`), gemischt (`COMBINED`) und Autobahn (`HIGHWAY`). Grundlage sind dieselben plausiblen Einträge wie bei Saison und Durchschnitt (SoC-Pfad zuerst, kWh/Strecke als Fallback), distanzgewichtet. Maßgeblich ist der Streckentyp des Ladevorgangs, der die Fahrt abschließt. Fahrten ohne Streckentyp zählen in keinem Topf, ebenso Ladungen, deren Streckentyp nur aus der Faustregel stammt (Herkunft `HEURISTIC`).

### Herkunft des Streckentyps (`route_type_source`)

Jeder Streckentyp an einer Ladung trägt seine Herkunft. Rangfolge: `USER` (im Formular gewählt) > `SOURCE` (von der Quelle geliefert: Spritmonitor-Notiz, API-Upload, Import-Datei) > `TRIPS` (aus Fahrten abgeleitet) > `HEURISTIC` (Faustregel) > ohne. Eine niedrigere Stufe überschreibt nie eine höhere; Altbestand ohne Herkunft darf von jeder Ableitung ersetzt werden. Live-Quellen (Tesla Live, Smartcar, go-e, EU Data Act Sync) liefern selbst nie einen Streckentyp.

### Ableitung aus Fahrten (`RouteTypeDerivationService`, Herkunft `TRIPS`)

Gilt pro Auto, egal aus welcher Quelle Ladung oder Fahrten stammen: eine Wallbox-Ladung bekommt ihren Typ aus den Tesla-Live- oder XPeng-Fahrten desselben Autos.

- Fenster: Ende der vorherigen Ladung (Beginn plus Dauer) bis Beginn der aktuellen. Ohne vorherige Ladung keine Ableitung.
- Fahrten ohne eigenen Typ werden nach Durchschnittstempo (Strecke / Dauer) bewertet: ab 80 km/h Autobahn, unter 50 km/h Stadt, dazwischen gemischt. Fahrten unter 0,5 km zählen nicht. Smartcar-Fahrten sind ausgeschlossen, bis deren Zeitstempel repariert sind.
- Ergebnis ist der Typ mit den meisten km, nur bei strikter Mehrheit, sonst gemischt.
- Mindestabdeckung: beide Ladungen brauchen einen Kilometerstand, und die Fahrten müssen mindestens die Hälfte der Odometer-Differenz abdecken. Sonst bleibt der Streckentyp leer.

### Faustregel ohne Fahrten (Herkunft `HEURISTIC`)

Greift nur, wenn die Fahrten kein Urteil liefern und die Ladung noch keinen Streckentyp hat (ein vorhandenes gemischt bleibt stehen). Signal ist die mittlere Entladeleistung zwischen zwei Ladungen: SoC-Hub (SoC-Ende der Vorladung minus SoC-Start der aktuellen) mal effektive, SoH-adjustierte Kapazität, geteilt durch die Stunden zwischen Ende der Vorladung und Beginn der aktuellen. Nur Lücken von 0,5 bis 12 Stunden zählen. Ab 25 kW gilt die Strecke als Autobahn. Stadt oder gemischt werden nie per Faustregel gesetzt, weil wenig verbrauchte Energie auch Stillstand sein kann. Dieser Wert zählt in keiner Verbrauchsstatistik je Streckentyp, er dient der Anzeige am Ladevorgang und später der gezielten Nachfrage im Wizard.

Auf der öffentlichen Modellseite (`routeTypeConsumption` in `PublicModelStatsResponse`) wird ein Typ erst ab 10 plausiblen Fahrten veröffentlicht, darunter ist der Wert `null`, die Anzahl bleibt sichtbar. Ohne eine einzige klassifizierte Fahrt ist das ganze Feld `null`. Der persönliche Reichweitenrechner nutzt den Autobahn-Wert für die Langstrecke.

## Verwendung in der App

| Kontext | Methode | Datenbasis |
|---------|---------|------------|
| Dashboard: Verbrauch pro Log | `calculateConsumptionPerLog()` | Logs des Fahrzeugs |
| User-Statistiken: Gesamtverbrauch | `calculateConsumptionWithSoc()` / `calculateConsumptionFallback()` | Logs des Fahrzeugs |
| Public Model: Community-Ø | `calculateCommunityAvgConsumption()` | Alle Autos des Modells |
| Public Model: Saisonale Aufschlüsselung | `calculateSeasonalConsumption()` | Alle Autos des Modells |
| Efficiency Ranking (`/rankings`) | `calculateCommunityAvgConsumption()` | Modelle mit >= 10 Logs, sortiert aufsteigend |

---

## Konfiguration: `PlausibilityProperties`

Konfigurierbar via `application.yml` unter dem Prefix `plausibility`:

```yaml
plausibility:
  absolute-min-kwh-per-100km: 10.0
  absolute-max-kwh-per-100km: 40.0
  wltp-lower-factor: 0.75
  wltp-upper-factor: 2.2
  sigma-multiplier: 2.5
  min-trips-for-statistical: 5
  min-trip-distance-km: 20
```

---

## Messbasis: Was ist `kwhCharged`?

`kwhCharged` bezeichnet die **am Ladegerät oder der Wallbox gemessene Energie** (Bruttowert). Das ist der Wert, den die Wallbox, der Ladeport oder die Abrechnung des Ladenetzbetreibers ausweist.

### Die drei Messebenen

| Level | Bezeichnung | Quelle | Typischer Wert |
|-------|------------|--------|----------------|
| 1 | **Brutto am Lader** | Wallbox, Ladesaule, OCPP | 450 kWh |
| 2 | **Ins Fahrzeug** | Fahrzeug-App (z.B. Tesla) | 420 kWh (-7%) |
| 3 | **Nur Fahrverbrauch** | Tessie / Fahrzeug-API | 347 kWh (-23%) |

Level 1 und Level 3 konnen um 20-30% differieren, da Level 3 Ladeverluste, Standby (Wachter-Modus), Klimatisierung im Stand und andere Nebenverbraucher ausklammert.

**EV Monitor standardisiert auf Level 1 (Brutto am Lader)**, weil:
- Das der Wert ist, den Wallboxen und Ladestationen universal ausweisen
- Er direkt zur Kostenkalkulation passt (was man bezahlt hat)
- Die meisten manuellen Einträge und alle OCPP/Wallbox-Integrationen diesen Wert liefern

### Auswirkung auf den WLTP-Vergleich

WLTP misst den Fahrverbrauch (Level 3). EV Monitor-Werte beinhalten Ladeverluste (Level 1). Eine systematische Abweichung von **10-20% über dem WLTP-Wert** ist deshalb normal - kein Zeichen für schlechte Effizienz. Das wird in der UI transparent kommuniziert.

### `EnergyMeasurementType` (implementiert, DB-seitig)

`measurement_type` wird in `ev_log` gespeichert und automatisch aus `dataSource` abgeleitet:

```java
enum EnergyMeasurementType {
    AT_CHARGER,   // Standard - Wallbox-Messung (Brutto)
    AT_VEHICLE,   // Ins Fahrzeug geladene Energie (nach Ladeverlusten)
    DRIVING_ONLY  // Nur Fahrverbrauch (z.B. Tessie, ohne Standby/Wachter)
}
```

**Automatische Zuweisung per DataSource:**
- `TESLA_LIVE`, `SMARTCAR_LIVE` → `AT_VEHICLE`
- alle anderen → `AT_CHARGER`

**Aktueller Stand:** Nur persistiert - keine Filterung in Community-Stats oder Consumption-Berechnung. Die Datenbasis ist vorhanden, sobald eine Normalisierung oder getrennte Darstellung nötig wird.

**Normalisierung in Berechnungen:** Seit V55 werden AT_CHARGER-Daten in allen Verbrauchsberechnungen auf AT_VEHICLE-Äquivalent normalisiert (siehe unten).

---

## Ladeverlust-Normalisierung

Da AT_CHARGER-Daten (Wallbox) systematisch ~5-10% höher sind als AT_VEHICLE-Daten (Smartcar/Tesla), würde ein unveränderter Mix beider Quellen Wallbox-User als weniger effizient erscheinen lassen.

### Koeffizienten (konfigurierbar via `application.yml`)

```
AC-Laden (Wallbox 11-22 kW):  Effizienz = 0.90  (10% Verlust - Onboard-Charger AC→DC)
DC-Schnellladen:               Effizienz = 0.95  (5% Verlust - Charger direkt ins Akku)
```

Quellen: ADAC 2022/2025-Studien, IEEE Sears et al. (1.008 reale Ladevorgänge).

### Anwendung: AT_CHARGER → AT_VEHICLE (für Verbrauch/Reichweite)

`effectiveKwhForConsumption(log)`: multipliziert AT_CHARGER-kwhCharged mit dem Effizienzfaktor.
Wird verwendet in: SoC-Formel (socBeforeLogY-Rekonstruktion), allen Fallback-Pfaden.
AT_VEHICLE-Logs (Smartcar, Tesla Live) werden unverändert verwendet.

### Anwendung: AT_VEHICLE → AT_CHARGER (für avgCostPerKwh)

`effectiveKwhForCost(log)`: dividiert AT_VEHICLE-kwhCharged durch den Effizienzfaktor.
Wird verwendet in: `avgCostPerKwh`-Berechnung in `getStatistics()`.
AT_CHARGER-Logs werden unverändert verwendet.

### AC vs. DC Bestimmung

1. `charging_type = DC` → DC-Koeffizient
2. `charging_type = AC` → AC-Koeffizient
3. `charging_type = UNKNOWN` + `is_public_charging = true` → DC (Proxy)
4. `charging_type = UNKNOWN` + `is_public_charging = false` → AC (Proxy, Heim-Wallbox)

**Ausstehend:** UI-Kommunikation der Normalisierung (Tooltip bei kWh/100km-Anzeige).

---

## Wichtige Designentscheidungen

**Kein SQL für Verbrauchsberechnungen:** Die SoC-Formel und der statistische Plausibilitätscheck lassen sich nicht sinnvoll in SQL abbilden. Alle Berechnungen laufen in Java. Eine spätere Materialisierung als DB-Spalte ist denkbar, sobald die Logik stabil ist.

**`includeInStatistics`-Flag:** Seed-Daten, Tesla-Imports ohne Kosten/Dauer und Test-Logs (`data_source` beginnt mit `TEST_`) werden von Community-Statistiken ausgeschlossen. Demo-Mode für Seed-User: `isSeedUser=true` schließt alle Seed-Daten ein.

**0€-Kosteneinträge:** Logs mit `costEur = 0` werden bei der Berechnung von `avgCostPerKwh` und `totalCostEur` nicht berücksichtigt - sowohl in den User-Statistiken (`EvLogService`) als auch in den Community-Stats (SQL). `totalKwhCharged` und `log_count` zählen weiterhin alle Logs unabhängig vom Preis.

**Nachladungen:** Logs mit identischem `odometerKm` wie der unmittelbar vorherige Log desselben Fahrzeugs gelten als Nachladungen (Auto hat sich nicht bewegt). Sie werden in `log_count` und `avgKwhPerSession` der Community-Stats nicht gezählt (`findPublicBasicStatsByModel` via LAG-Window-Function). Betrifft nicht die Verbrauchsberechnung (kWh/100km).

**Distanzgewichtung statt Durchschnitt:** Ein Auto mit 10.000 km Daten hat 10× mehr Gewicht als eines mit 1.000 km. Das verhindert, dass einzelne Ausreißer-Logs den Community-Schnitt dominieren.

**Gesamtstrecke unabhängig von Verbrauchsplausibilität:** `totalDistanceKm` (Gesamtstrecke im Dashboard) wird direkt aus den Odometer-Deltas aller Logs berechnet - unabhängig davon, ob die Verbrauchsberechnung für einen Log plausibel ist oder nicht. Ein Partial-Charge (z.B. kleiner Top-Up auf 60% SoC) kann einen implausibel niedrigen kWh/km-Wert erzeugen, aber die gefahrenen Kilometer sind trotzdem eine Tatsache aus dem Odometer.

---

## Die Energiekette: Warum EV Monitor höher anzeigt als der Bordcomputer

Dieser Abschnitt erklärt die Hintergründe für Nutzer die sich fragen, warum EV Monitor systematisch einen höheren Verbrauch anzeigt als der Fahrzeug-Bordcomputer.

### Drei Messpunkte, drei verschiedene Werte

Jede kWh die ein E-Auto bewegt durchläuft mehrere Stationen - und an jeder Station wird anders gemessen:

```
Stromnetz
    │
    │  24,82 kWh  ← was du an der Ladesäule bezahlst (AT_CHARGER)
    ▼
Ladekabel / Onboard-Charger
    │
    │  ~0,82 kWh Ladeverlust (Wärme, AC→DC-Wandlung)
    │
    │  24,00 kWh  ← was im Akku ankommt (AT_VEHICLE)
    ▼
Akku
    │
    │  Standby-Verbrauch: Klimatisierung, Software, Fahrzeug-Systeme im Stand
    │
    ▼
Fahrt beginnt
    │
    │  Y kWh ← was der Motor während der Fahrt zieht (Bordcomputer)
    ▼
Fahrt endet
    │
    │  Standby-Verbrauch bis zum nächsten Ladevorgang
    │
    ▼
Nächster Ladevorgang
```

**Reales Beispiel** (AC-Laden, 04.04.2026):
- Am Netz gemessen (AT_CHARGER): **24,82 kWh** - das ist die Stromrechnung
- Im Akku angekommen (AT_VEHICLE): **24,00 kWh** - Ladeverlust: 0,82 kWh (3,3%)
- SoC-Veränderung: 37% → 80%, also +43 Prozentpunkte

### Was jede Messmethode erfasst

**AT_VEHICLE** (über Fahrzeug-APIs wie Smartcar oder Tesla Fleet)
Der Wert kommt direkt vom Fahrzeug bzw. dessen Hersteller-API - EV Monitor übermittelt ihn unverändert. Er repräsentiert die Energie die tatsächlich in den Akku geflossen ist, nach Abzug der Ladeverluste. Das ist die präziseste Grundlage für die Verbrauchsberechnung.

**AT_CHARGER** (manuelle Eingabe, Wallbox, Ladesäule)
Dieser Wert stammt von der Ladestation - also der Netzseite vor den Ladeverlusten. EV Monitor rechnet den typischen Ladeverlust heraus (AC: ~10%, DC: ~5%) bevor der Wert in die Verbrauchsberechnung einfließt.

**Bordcomputer**
Der Bordcomputer misst ausschließlich was der Motor während der Fahrt gezogen hat. Standby-Verbrauch im Stand - vor der Fahrt und nach der Fahrt bis zum nächsten Laden - ist darin nicht enthalten. Deshalb zeigt der Bordcomputer systematisch weniger an als EV Monitor berechnet. Das ist kein Fehler, sondern ein Unterschied in der Frage die gestellt wird.

### Was EV Monitor misst - und warum

EV Monitor berechnet den Verbrauch zwischen zwei Ladevorgängen:

```
Verbrauch = (kWh geladen + SoC-Korrektur) / Strecke × 100
```

Die **SoC-Korrektur** gleicht aus, dass nie von 0% auf 100% geladen wird:
- War der Akku nach dem letzten Laden voller als jetzt nach diesem Laden? → mehr wurde in dieser Periode verbraucht als jetzt geladen wurde
- War er weniger voll? → ein Teil des Ladestroms ist "gespart", nicht verbraucht worden

Damit erfasst EV Monitor die **gesamte Energie die zwischen zwei Ladevorgängen verbraucht wurde** - inklusive Standby-Verlusten im Stand. Diese Energie ist real verloren gegangen, auch wenn nicht jede kWh den Asphalt berührt hat.

### Warum der Unterschied zum Bordcomputer normal ist

Unabhängige Forschung zeigt: der reale Energiebedarf liegt im Schnitt **1,75-2 kWh/100km höher** als der Bordcomputer anzeigt. Standby-Verluste allein machen je nach Fahrzeug, Außentemperatur und Software-Version **1-3 kWh pro Tag** aus.

Das bedeutet: wenn EV Monitor 17 kWh/100km berechnet und der Bordcomputer 15 kWh/100km anzeigt, liegt EV Monitor näher an dem was das Fahren tatsächlich kostet.

### Die unvermeidbare Unschärfe

Keine Messmethode ist perfekt:

- **SoC-Messungen** vom BMS haben ganzzahlige Auflösung (1% entspricht je nach Akkugröße ca. 0,5-0,7 kWh) und setzen einen korrekten SoH-Wert voraus
- **kWh-Werte vom Fahrzeug** werden vom Fahrzeug-BMS oder der Hersteller-API geliefert - Smartcar oder ähnliche Dienste übermitteln dabei nur, was das Fahrzeug selbst meldet
- **kWh-Zähler an Ladestation und Wallbox** haben herstellerabhängige Messtoleranzen
- **Standby-Verluste** variieren je nach Außentemperatur, Software-Version und Nutzungsverhalten

EV Monitor verwendet den Median über mehrere Ladevorgänge um Ausreißer herauszuglätten, und gibt für jeden einzelnen Trip einen Plausibilitäts-Indikator an.

### Quellen

- [Electric vehicles' energy consumption measurement and estimation (ResearchGate)](https://www.researchgate.net/publication/267928696_Electric_vehicles'_energy_consumption_measurement_and_estimation)
- [Real-world study for optimal charging of electric vehicles (ScienceDirect)](https://www.sciencedirect.com/science/article/pii/S2352484719310911)
- [How do you measure power consumption of an EV? (EVLife)](https://evlife.world/tech/how-does-ev-power-consumption-information-work/)
