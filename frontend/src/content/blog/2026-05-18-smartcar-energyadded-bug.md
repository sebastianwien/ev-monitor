---
title: "Wenn Smartcar 'nicht kann': Wie wir einen halbierten Ladevorgang aufgespürt haben"
description: "Ein Skoda Enyaq lädt 19 kWh laut Skoda Connect, unsere App zeigt nur 8.71 kWh. Spurensuche durch Webhook-Logs, OEM-Cloud-Latenzen und die Smartcar Compatibility Matrix."
slug: "smartcar-energyadded-compatibility-matrix"
date: "2026-05-18"
author: "Sebastian"
---

## Ausgangspunkt

Ein User meldet eine Diskrepanz für einen Ladevorgang seines Skoda Enyaq iV 60, der über AutoSync via Smartcar erfasst wurde. Die Skoda Connect App zeigt rund 19 kWh über 13h 9min. Unser ev_log-Eintrag zur selben Session: 8.71 kWh, 60 Minuten, SoC 63 Prozent auf 80 Prozent. Auf den ersten Blick fehlen 10 kWh.

Der User merkt an, dass ihm das Problem alle zwei bis vier Ladevorgänge unterkommt. Diagnose-Modus.

## Befund 1: Wir verpassen den Charging-Start regelmäßig

Auswertung der `smartcar_webhook_raw_log`-Tabelle für das betroffene Fahrzeug, Zeitraum 16. bis 17. Mai, alle Zeiten UTC:

```
16.05. 14:00:37   SoC=49%   odo=79607.0   is_charging=false
16.05. 16:01:05   SoC=46%   odo=79618.0   is_charging=false
[1h 59min keine Webhooks]
16.05. 18:00:49   SoC=63%   odo=79618.0   is_charging=true     unser Session-Start
16.05. 19:00:52   SoC=80%   odo=79618.0   is_charging=false    unser Session-Ende
17.05. 07:00:54   SoC=79%   odo=79620.0   is_charging=false
```

Zwischen 16:01 und 18:00 UTC fließt nichts. Der Wagen steht (Odometer unverändert), hat aber zwischen den beiden Snapshots 17 Prozent SoC zugelegt. Der Plug-in war laut Skoda Connect 17:05 UTC, also rund 55 Minuten vor unserem ersten `is_charging=true`-Event.

Aus der Smartcar-Dokumentation: Webhooks sind change-driven. Ein Event feuert nur wenn sich ein abonniertes Signal ändert. Für Skoda EU dokumentiert Smartcar eine "Data Freshness" von 30 bis 60 Minuten. Der OEM-Cache der Skoda WeConnect-Cloud aktualisiert sich nur in diesem Intervall.

Der Webhook um 18:00 UTC hatte für `IsCharging` einen `oemUpdatedAt` von 18:00:41 UTC und einen `fetchedAt` von 18:00:46 UTC. Smartcar war also nicht latent. Die Verzögerung sitzt zwischen Auto und Skoda-Cloud, nicht zwischen Smartcar und uns.

Konsequenz: Unsere Session startet mit `socStart = 63%` statt 46. Die Energie wird aus SoC-Delta gerechnet (siehe Befund 2), Delta 17 statt 34 Prozent, der kWh-Wert ist exakt halbiert.

Fix-Pattern im Smartcar-Connector: Beim Übergang in eine neue Session den letzten Webhook für dieselbe Vehicle-ID nachschlagen. Wenn die Guards greifen (höchstens 24h alt, `is_charging=false`, gleicher Odometer beide non-null, strikt niedrigerer SoC), dann `socStart` aus dem vorherigen Webhook übernehmen. `session_started_at` bleibt `now()`, weil der echte Plug-in-Zeitpunkt unbekannt ist. Lieber Dauer zu kurz als gelogene Start-Zeit.

## Befund 2: Smartcar liefert `Charge.EnergyAdded` für fast alle EU-BEVs nicht

Aggregation der `smartcar_webhook_raw_log` über 10 Tage, gruppiert nach Marke:

| Make | Webhooks | mit energyAdded | ohne energyAdded |
|------|----------|-----------------|------------------|
| TESLA | 3250 | 3250 | 0 |
| POLESTAR | 1310 | 0 | 1310 |
| VOLKSWAGEN | 840 | 0 | 840 |
| SKODA | 194 | 0 | 194 |

Die Webhook-Payloads von Polestar, VW und Skoda enthalten für `Charge.EnergyAdded` ein `status.error.code = VEHICLE_NOT_CAPABLE`. Bestätigung über die offizielle Smartcar Compatibility Matrix: von 235 EU-BEV-Modellen unterstützen genau drei Marken das Signal `Charge.EnergyAdded`:

- Tesla (Model 3, S, X, Y)
- Jaguar I-PACE
- Land Rover Range Rover Evoque

Die anderen 229 Modelle (Audi, BMW, BYD, Hyundai, Kia, Mercedes, Polestar, Renault, Skoda, Volvo, VW etc.) liefern es nicht.

Praktisch heißt das: Für alle non-Tesla-EU-Smartcar-Verbindungen muss die geladene Energie aus `SoC-Delta × effective_capacity` zurückgerechnet werden. Unsere Connector-Methode hat dafür einen Fallback, der die effective Capacity vom Backend per REST holt. Das ergibt einen plausiblen Wert, aber keinen gemessenen.

## Befund 3: Skoda Connect zeigt eine Buchhaltungsgröße, keinen Messwert

Skoda Connect zeigt 19 kWh. Forenrecherche (Enyaq-Forum, VWIDtalk, GoingElectric) und Wallbox-Cross-Checks von Usern: die App rechnet `SoC-Delta × Nenn-Nettokapazität`, ohne SoH-Korrektur.

Bei unserem Fall: 34 Prozent SoC-Delta × 58 kWh Nenn-Netto = 19.72 kWh, gerundet 19. Match.

Wallbox-AC-Messungen liegen typischerweise 5 bis 10 Prozent über dem App-Wert, weil der Onboard-Charger AC zu DC mit etwa 90 Prozent Wirkungsgrad wandelt. Die Skoda-App zeigt also DC-Netto auf Basis des Nennwerts, nicht die echt geflossene Energie.

Konsequenz: Skoda's 19 kWh ist nicht direkt als Ground Truth nutzbar. Es ist eine SoC-basierte Rechnung mit Annahme SoH = 100 Prozent.

## Befund 4: Self-referential Loop im SoH-AutoDetect

Hier wurde es interessant. Unser Backend hat einen `BatterySohAutoDetector`, der aus AT_VEHICLE-Ladelogs die effective Capacity zurückrechnet und daraus die State of Health pro Fahrzeug schätzt. Formel pro Log:

```
estimated_capacity = effectiveKwh(log) × 100 / socDelta
```

Über die letzten 5 qualifying Logs wird der Median gebildet, dann gegen den Brutto-Nominalwert (`car.batteryCapacityKwh`, hier 62) geteilt um die SoH zu erhalten.

Bei SMARTCAR_LIVE-Sessions ohne `energyAdded` ist `kwh_at_vehicle` aber selbst das Ergebnis von `SoC-Delta × effective_capacity / 100` aus dem Connector. Eingesetzt:

```
estimated_capacity = (SoC-Delta × effective_capacity / 100) × 100 / SoC-Delta
                   = effective_capacity
                   = net_battery_capacity_kwh × (1 - SoH/100)
```

Die SoC-Delta-Werte kürzen sich raus. Wir bekommen unsere bereits angenommene effective Capacity zurück. Keine neue Information aus den Daten.

Dividiert durch Brutto:

```
geschätzter_SoH = aktuelle_effective_capacity / brutto
                = (netto × aktueller_SoH / 100) / brutto
                = aktueller_SoH × (netto / brutto)
                = aktueller_SoH × (58/62)
                = aktueller_SoH × 0.9355
```

Pro AutoDetect-Run multipliziert sich die SoH mit 0.9355. Verifikation an den realen DB-Daten für das Fahrzeug:

| Datum | SoH |
|-------|-----|
| 2026-04-17 | 94.00% |
| 2026-05-04 | 88.33% |

Vorhersage aus der Formel: `94.00 × 0.9355 = 87.94`, gerundet 88.33. Match.

Die SoH driftet bei jedem Lauf systematisch nach unten, bis der `SOH_CHANGE_THRESHOLD` greift. Die effective Capacity wird kleiner, also wird der nächste `kwh_at_vehicle`-Wert auch kleiner, also wird die nächste Median-Schätzung wieder kleiner. Tautologie statt Lernsignal.

## Was wir adressiert haben

### Schritt 1: Marker für die Herkunft

Migration V119 fügt `ev_log.energy_source` als nullable VARCHAR(20) hinzu, mit CHECK auf vier Enum-Werte: `OEM_MEASURED`, `SOC_INFERRED`, `USER_INPUT`, `WALLBOX`. Java-Enum, Domain, Entity, Repository spiegeln das Pattern bestehender String-Enums.

`BatterySohAutoDetector.isQualifying` schließt `SOC_INFERRED` aus, NULL bleibt qualifying (backwards-kompatibel für historische Logs).

### Schritt 2: Connector schreibt den Marker

`SmartcarApiService.calculateKwh` gibt jetzt ein `KwhResult`-Record (`kWh + EnergySource`) zurück statt nur einer Zahl. Die beiden energyAdded-basierten Pfade markieren als `OEM_MEASURED`, der SoC-Delta-Fallback als `SOC_INFERRED`. `VwGroupChargingSessionService` analog. Backend nimmt den String über `InternalEvLogRequest` entgegen und reicht ihn ins Domain durch.

Effekt: Neue Logs aus dem SoC-Fallback-Pfad sind ab sofort als inferiert markiert und fließen nicht mehr in die SoH-Schätzung ein. Der Drift stoppt für die Zukunft.

### Bonus-Fix: socStart-Backfill bei verspäteten Charging-Webhooks

Im selben Atemzug haben wir den ursprünglichen 19-vs-8.71-kWh-Fall direkt adressiert. Beim Übergang auf `is_charging=true` wird der vorherige Webhook geprüft. Wenn er weniger als 24h alt ist, das Auto stand (gleicher Odometer) und niedrigeren SoC zeigte, wird sein SoC als `socStart` der neuen Session genommen. Im konkreten Fall heißt das: statt 17 Prozent Delta erfassen wir die echten 34 Prozent.

## Was noch offen ist

Historische Logs vor V119 haben `energy_source = NULL` und sind damit weiter qualifying. Die Drift-Erholung greift erst, wenn genug neue, markierte Logs das 5er-Median-Window füllen. Eine rückwirkende Migration über die `data_source`-Spalte ist möglich (alle SMARTCAR_LIVE / VWGROUP_LIVE auf `SOC_INFERRED` setzen, alles andere auf `OEM_MEASURED` oder seine Variante), aber das ist ein eigener Schritt mit eigenem Risikoprofil.

Der `EvLogService` als zentrale Verbrauchsberechnung filtert `SOC_INFERRED`-Logs noch nicht aus Verbrauchs- und Community-Statistiken. Das berührt die zentrale Rechen-Schicht und braucht eine eigene Entscheidung: bei strikter Filterung verliert die UX an Daten für non-Tesla-User in der Übergangsphase, bei laxer Filterung bleiben verzerrte Werte in Aggregaten sichtbar.

Tesla, Wallbox und User-Manual-Endpoints setzen ihre Marker (`OEM_MEASURED`, `WALLBOX`, `USER_INPUT`) noch nicht. Außerhalb des akuten Bug-Fix-Pfads, aber nötig damit die Filter-Logik vollständig ist.

## Take-Aways

Die Skoda-Story war eigentlich drei Bugs in einem Trenchcoat:

1. Smartcar-Webhook-Latenz im OEM-Cache lässt uns den realen Charging-Start verpassen
2. EU-non-Tesla-Brands liefern strukturell keinen `energyAdded`, also rechnen wir SoC-Delta-basiert
3. Diese Rechnung wird vom SoH-AutoDetect zurückgekehrt und produziert einen self-referential Loop

Was wir gelernt haben: Wenn ein Datenpunkt aus einer Annahme abgeleitet ist, darf er nicht in die Schätzung dieser Annahme zurückfließen. Bei OEM-Daten lohnt es sich, in der Vendor-Compatibility-Matrix nachzusehen, bevor man Eligibility-Logik schreibt. Und bei einer Diskrepanz zwischen zwei "Wahrheiten" (OEM-App vs. eigene App) hilft es, beide Berechnungswege bis zur Quelle nachzuvollziehen, weil oft beide nicht messen sondern interpolieren.
