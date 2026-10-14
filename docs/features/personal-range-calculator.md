# Persönlicher Reichweitenrechner

Auf jeder Modellseite als Fahrprofil plus Ergebnis-Box (siehe `public-models.md`). Die Modellwerte der Seite bleiben frei, nur das persönliche Ergebnis setzt ein Konto voraus.

## Eingaben

Fünf Schieberegler im Fahrprofil. Ein Regler gilt erst als beantwortet, wenn er bewegt wurde; offen steht "noch offen", der Knopf ist grau, die Spur leer. Beim Ziehen zählt der Wert live, als Antwort das Loslassen. "Kostenlos freischalten" mit offenen Reglern springt zum ersten offenen Regler (markiert, Fokus), die Schloss-Box zeigt "Noch 2 Fragen offen" und darunter "Schieb jeden Regler einmal auf deinen Wert."

- Kilometer pro Jahr (5.000 bis 75.000 in 1.000er-Schritten, Start 15.000; Request `annualKm` 1.000 bis 100.000)
- Davon Autobahn in km pro Jahr (0 bis zur beantworteten Fahrleistung, sonst 75.000, in 500er-Schritten, Start 4.000; darunter der Anteil "40 % deiner Fahrleistung"). Der Request trägt weiter `highwaySharePercent`, die Seite rechnet Autobahn-km ÷ Jahreskilometer um (höchstens 100); ein gespeichertes Profil wird von Prozent in km zurückgerechnet.
- Arbeitsweg pro Tag, hin und zurück (5 bis 150 km in 5er-Schritten, Start 40; Request 0 bis 500)
- Pendeltage pro Woche (0 bis 6, Start 5; Request `commuteDaysPerWeek` 0 bis 6, in älteren Profilen leer und dann als 5 gerechnet)
- Längste Urlaubsfahrt (250 bis 2.000 km in 50er-Schritten, Start 500; Request 50 bis 2000)

Nach dem Freischalten, in einer eigenen Karte unter den Antworten, rechnen live nach:

- "Hast du zu Hause eine Wallbox?" Ja/Nein als zwei Knöpfe, mit Erklärung, warum das zählt. Vorher rechnet das Backend mit Laden zu Hause (oder mit der Antwort aus dem Bedarfs-Check der Rangliste).
- Strompreis als Regler (0,10 bis 1,00 €/kWh, Request 0,05 bis 1,50). Startwert ist der Community-Preis, den das Backend passend zur Wallbox-Antwort nimmt (AC bzw. DC), bis der Nutzer selbst schiebt; "Zurück zum Community-Preis" setzt ihn zurück.
- Netto-Akku der gewählten Variante (10 bis 250 kWh) geht immer mit; fehlt er, gilt die größte WLTP-Variante.

In Meilen-Märkten in Meilen, gerechnet wird in km. Ein gespeichertes Profil setzt die Regler auf den nächstgelegenen Schritt und zählt als beantwortet. Alle fünf Regler teilen sich mit dem Bedarfs-Check der Rangliste (dort dieselben fünf Regler) den Browser-Speicher `ev-needs-check-v2`: dort beantwortet zählt hier als beantwortet und umgekehrt (nur eigene Eingaben werden zurückgeschrieben). Die Wallbox-Antwort geht nur zurück, wenn der Nutzer sie nach dem Freischalten aktiv gibt, nie der Startwert. Ein Server-Profil gewinnt. Ein Chip aus der Partner-Kachel kommt als `?pendeln=<km>` oder `?urlaub=<km>` an, belegt Pendelstrecke bzw. Urlaubsfahrt vor und gewinnt gegen den Browser-Speicher, nicht gegen ein Server-Profil. Ein Profil von vor der Pendeltage-Frage lässt genau diesen Regler offen. Gespeichert wird das Profil bei jeder angemeldeten Rechnung, ohne den Akku des Modells.

Die Ergebnis-Mail zeigt die Langstrecke wie die Seite als Reisezeit ("Unterwegs auf 500 km: 6 h 20 min, inkl. 1 Ladestopp, grob 35 min · Ø 110 km/h") und nennt im Profil die Jahreskilometer, wenn angegeben, und das Pendeln ("Pendeln 40 km an 3 Tagen"), dazu Autobahnanteil und Langstrecke; die Ladezeile spricht wie die Seite ("alle 10 Tage", `ChargeRhythm.java` mit derselben Tabelle wie `chargeRhythm.ts`), die Kostenzeile nennt Fahrleistung und kWh-Preis.

## Ergebnis-Box

Eine Box, drei Zustände (`PersonalRangeCalculator`):

1. **Gesperrt:** "Wir sagen dir:" mit den vier Fragen (Wie weit komme ich im Winter? Wie oft muss ich laden? Wie viele Stopps auf {Urlaubsfahrt}? Was kostet mich das im Monat?), darunter verschwommene Platzhalter ohne Zahlen hinter einem geschlossenen Schloss ("Noch {n} Fragen offen" bzw. "Deine 4 Antworten sind bereit"), die Datenbasis (ab 5 Autos "Aus {n} echten Ladevorgängen von {m} {Modell}.") und für Gäste "Kostenlos und ohne Abo. Deine E-Mail nutzen wir nur für dein Ergebnis und dein Konto.", dann "Kostenlos freischalten". Vor dem Login steht keine Ergebniszahl im DOM.
2. **Gate:** dieselbe Box wird grün, das Schloss öffnet sich, darin Google-Button und Mail-Code (`InlineGate`, Google-Button als `GoogleButton` auch ohne Google-Script). Kleinzeile: "Wir nutzen deine E-Mail nur für dein Ergebnis und dein Konto. Löschen kannst du beides jederzeit in den Einstellungen." plus Link Datenschutz.
3. **Ergebnis:** vier Antworten plus eine fünfte Kachel mit Wallbox-Frage (linksbündig, Frage in Antwortgröße) und Strompreis-Regler. Über den Antworten liegt ein weißer Streifen mit dem gesetzten Profil in einer Zeile ("20.000 km im Jahr · 8.000 km Autobahn · 35 km Arbeitsweg · 3 Tage pendeln · 500 km Urlaubsfahrt") und rechts "Angaben ändern": der Streifen fährt animiert auf und zeigt die fünf Regler in der kompakten Form der Rangliste (ab 1280px eine Reihe, darunter drei bzw. zwei Spalten, Handy eine Spalte). Der Sprung aus der Frage-Karte im Hero öffnet ihn. Unter der Box bleibt "Worauf das Ergebnis beruht". Die Stopps und Ladekosten der Urlaubsfahrt gelten für die einfache Strecke mit voller Batterie am Start. Jede Änderung an Fahrprofil, Wallbox oder Preis rechnet nach 400 ms neu.

Layout: solange die Box gesperrt ist oder das Gate zeigt, steht sie ab 768px rechts neben den Reglern und bleibt beim Scrollen stehen. Sobald die Antworten offen sind, nimmt sie die volle Breite (Antworten zwei mal zwei, Wallbox und Preis darunter über beide Spalten), die eigene Regler-Box verschwindet und die Regler liegen in der Klappe "Deine Angaben ändern". Scrollt der Leser zu den aufgeklappten Reglern, folgt eine schmale Leiste mit den vier Zahlen unter der Navigation, die beim Schieben live mitläuft und zurück zum Ergebnis springt. Auf dem Handy gilt dasselbe.

Angemeldete mit vollständigem Profil sehen die Antworten sofort, ohne Klick.

Der öffentliche Endpoint `POST /api/public/personal-range/{brand}/{model}/preview` (Spanne Winter bis Sommer, 30 je IP und Stunde) wird vom Frontend nicht mehr gerufen.

Die Rangliste rechnet über `POST /api/public/personal-range/ranking` (öffentlich, 60 je IP und Stunde, speichert nichts): dieselbe Anfrage wie die Modellseite ohne Akku, Antwort je gelistetem Modell `summerRangeKm`, `winterRangeKm`, `longTripStops`, `longTripTotalMinutes`, `chargesPerWeekSummer`/`Winter`, `monthlyCostEur`, `monthlyKm` und die Stopp-Leiter `zeroStopReachKm`/`oneStopReachKm`/`twoStopsReachKm`. Modelle ohne WLTP-Spec fehlen. Alle WLTP-Specs kommen in einer Abfrage, Modellstatistik und Preise aus dem Cache; der Cache-Warmup wärmt die Statistik aller gelisteten Modelle (kalt lokal 13,6 s für 61 Modelle, warm 11 ms).

## Ablauf

- Gast drückt "Kostenlos freischalten": die Box wird zum Gate. Ab 5 Autos nennt die Schloss-Box die echten Ladevorgänge und Autos dahinter; darunter sagt sie offen, dass sie aus Herstellerangaben rechnet, wo Messwerte fehlen. Anmeldung per Google oder Mail-Code ohne Seitenwechsel (siehe `authentication.md`); das Ergebnis kommt einmal auch per Mail.
- Die Eingaben liegen bis zum Ergebnis in `sessionStorage`, damit sie auch einen Login per Redirect überstehen. Ist man nach der Rückkehr angemeldet, rechnet die Seite automatisch.
- Angemeldete Nutzer bekommen das Ergebnis direkt. Die Eingaben werden am Konto gespeichert (`app_user.range_profile`) und auf jeder anderen Modellseite vorbefüllt.
- Die Einführung (Onboarding) erscheint erst auf der ersten App-Seite, nicht über dem Ergebnis.

## Antworten

Eine Liste ganzer Sätze mit Icon, die Zahl ist der laute Teil:

- **Winter:** "Im Winter kommst du ca. 230 km weit, im Sommer ca. 300 km.", darunter "100 % → 10 %" mit Akku-Symbolen wie im Tacho.
- **Laden:** gleiche Worte wie die Rangliste (`chargeRhythm.ts`): zu Hause "täglich" oder "alle n Tage" (7 ÷ Ladungen, abgerundet), unter einer Ladung in zwei Wochen "alle n Wochen", ohne Wallbox ab einer Ladung pro Woche "n-mal pro Woche" (aufgerundet), darunter dieselben Tage- und Wochen-Worte wie zu Hause. Gleich in beiden Jahreszeiten eine Zeile ("Normal wirst du ca. alle 4 Tage laden müssen."), sonst Sommer und Winter getrennt. Darunter "195 km Pendelstrecke pro Woche" (Arbeitsweg × Pendeltage), denn der Ladetakt rechnet nur das Pendeln.
- **Urlaubsfahrt:** "Auf deiner Urlaubsfahrt über 650 km wirst du wahrscheinlich 2-mal laden.", darunter mit Icons "6 h 55 min unterwegs", "davon 1 h laden" und "rund 57 € fürs Laden" (Stopps × Netto × 0,7 ÷ 0,95 × Schnellladepreis ohne Abo, nur im deutschen Markt); geschätzte Ladeleistung wird dort genannt.
- **Kosten:** "Es kostet dich 46 € im Monat, dein Auto aufzuladen.", darunter "4,60 €/100km bei 12.000 km im Jahr". Im deutschen Markt der Vergleich "Ein vergleichbarer Benziner kostet rund 122 €, du sparst 76 € im Monat." mit der Basis "Kompakt 6,8 l/100 km Benzin bei 1,80 € je Liter" (Liter je Fahrzeugklasse aus `COMBUSTION_LITERS_BY_CLASS`, Tagespreis aus Tankerkönig; unbekannte Klasse ohne Klassennamen).

Darunter die Datenlage (`MEASURED`, `PARTIAL`, `WLTP_FACTOR`) und jede Annahme mit "gemessen" oder "geschätzt"; die Schnellladeleistung ohne eigene Messung heißt "Mittel der gemessenen Modelle", das Reisetempo "festgelegt".

## Rechnung

Endpoint `POST /api/personal-range/{brand}/{model}` (nur angemeldet), Profil `GET /api/personal-range/profile` (204 ohne Profil). Logik in `PersonalRangeCalculator`, Datenbeschaffung in `PersonalRangeService`.

- Akku: der im Request genannte Netto-Akku der gewählten Variante, sonst größte WLTP-Variante, Netto-Kapazität, sonst nominal × 0,9 (dann als geschätzt markiert).
- Verbrauch Sommer/Winter: Saison-Werte des Modells ab 10 Fahrten je Saison, sonst WLTP × 1,15 bzw. × 1,35.
- Ohne eigene Messwerte eines Modells: WLTP mal Flotten-Faktor. Der Flotten-Faktor ist der Median aus "Community-Verbrauch ÷ WLTP" über alle Modelle mit mindestens 5 Autos, getrennt für Sommer, Winter und Gesamt (Alltag), berechnet aus der gecachten Top-Models-Liste. Unter 10 Modellen gelten feste Faktoren (Sommer 1,15, Winter 1,49, Gesamt 1,21). Median statt Mittel, damit weder beliebte Modelle noch Ausreißer die Flotte bestimmen.
- Kosten pro Monat an der Steckdose: Netto-Verbrauch geteilt durch die Ladeeffizienz (AC zu Hause, DC öffentlich, Werte aus der Verbrauchskonfiguration).
- Verbrauch Autobahn: Mischung aus Streckentyp-Messwert (ab 10 Fahrten) und Schätzung, gewichtet nach Anzahl der Autos hinter dem Messwert. Die Schätzung ist der Basisverbrauch (Fahrer-Ø, sonst WLTP × Flottenfaktor) mal Autobahn-Effekt (gemessen ab 5 Autos, sonst `HIGHWAY_RATIO_FALLBACK` 1,03); ein Modell, das deutlich unter WLTP gefahren wird, bleibt das auch auf der Autobahn. Unter 5 Autos nur die Schätzung, ab 5 Autos halb gemessen, linear steigend bis 15 Autos voll gemessen. Die Annahmen-Liste zeigt den gemessenen Anteil in Prozent; mit mehr Nutzern wandert der Wert automatisch zum Messwert. Verbrauch abseits der Autobahn: Stadt-Wert, sonst Modelldurchschnitt, sonst WLTP × 1,15.
- Alle gemessenen Werte stammen aus `EvLogService` über `PublicModelService`.
- Reichweite Sommer und Winter: 90 % des Netto-Akkus durch den Saisonverbrauch des Profils, auf 10 km gerundet. Profil heißt: der Autobahn-Effekt wird mit dem Autobahnanteil auf den Saisonwert gelegt. Der Effekt ist der gemessene Autobahnverbrauch geteilt durch das Mittel aller Fahrten mit Streckentyp (`typedMeanKwhPer100km`, dieselben Fahrten und derselbe Zeitraum, nicht der Gesamtschnitt seit 2020, der ältere Winter ohne Streckentyp enthält). Er darf unter 1 liegen, wenn die Autobahn gemessen sparsamer ist. Unter 5 Autos mit Autobahnwerten gilt der Flottenmedian `HIGHWAY_RATIO_FALLBACK` (1,03, gemessen am 11.10.2026), nie WLTP-Schätzung gegen Messwert. Die Belege der Modellseite zeigen den Effekt relativ ("Autobahn +7 %"). Bei 0 % Autobahn sind das die Saisonreichweiten des Modells, wie die Seite sie zeigt. Pendelstrecke, Langstrecke und Preis bestimmen Stopps, Ladungen und Kosten.
- Langstrecke mit dem Autobahn-Verbrauch (`LongTripPlan`): erste Etappe 100 auf 10 % (dasselbe Fenster wie die Reichweite, `RangeWindow`), jede weitere 10 auf 80 % (`FastChargeStop`). Ladezeit je Stopp = 70 % des Netto-Akkus durch die Schnellladeleistung, Fahrzeit bei festen 110 km/h im Schnitt (Annahme `TRAVEL_SPEED`), Lade- und Gesamtzeit auf 5 Minuten gerundet (`longTripChargingMinutes`, `longTripTotalMinutes`).
- Schnellladeleistung aus `FastChargePowerTable` (eine Abfrage über alle Modelle, Cache `fastChargePower`): die eigene Messung des Modells ab 8 passenden DC-Ladungen (`MEASURED`), sonst der Median der gemessenen Modelle (`FLEET_MEDIAN`, als geschätzt markiert), ohne jede Messung 104 kW (`DEFAULT`). Dieselbe Tabelle speist Rangliste und Datenblatt der Modellseite; dort erscheint nur der eigene Messwert. Eine Datenblatt-Ladeleistung je Variante ist als nächste Quelle zwischen Messung und Median vorgesehen.
- Fahrleistung: Mischverbrauch nach Autobahnanteil. Mit `annualKm` gelten Jahreskilometer ÷ 12 je Monat für die Kosten, sonst Arbeitsweg × Pendeltage × 4,4 Wochen (22 Arbeitstage auf 5 Tage). Ladungen pro Woche fürs Pendeln getrennt nach Saison (`chargesPerWeekSummer`, `chargesPerWeekWinter`): Pendelstrecke × Pendeltage mal Saisonverbrauch (mit Autobahnanteil) durch die Ladungsgröße (zu Hause 20 auf 80 %, sonst 10 auf 80 %), ohne Rundung, zwei Nachkommastellen. Dieselbe Regel rechnet die Rangliste. `chargesPerWeek` (aus Jahresleistung ÷ 52, auf halbe Ladungen aufgerundet) bleibt für ältere Seiten.
- Kosten pro Monat: Monatskilometer (siehe Fahrleistung) × Mischverbrauch × eigener Preis (Annahme `OWN_PRICE`, "deine Angabe"), sonst AC-Preis (zu Hause) bzw. DC-Preis des Modells (`PRICE` gemessen), sonst Community-Referenzpreis (`PRICE` geschätzt). Die Ergebnis-Mail nennt Preis und Basis in der Kostenzeile ("30.000 km im Jahr" oder "Pendeln an 22 Tagen").

## Plausible-Events

`personal_range_started` (erster Regler), `story_chip_answered` (Prop `question`), `personal_range_unlock_click` (Prop `open`, offene Regler), `personal_range_gate_shown`, `personal_range_result` (Prop `data_level`), `personal_range_wallbox` (Prop `home`), `model_profile_jump` (Frage-Karte im Hero), `gate_code_requested`, `gate_code_verified` (Prop `placement`, etwa `modell:tesla/model_3` oder `modelle:rangliste`). Goals müssen in Plausible angelegt werden; neu sind `personal_range_unlock_click`, `personal_range_wallbox`, `model_profile_jump`.

Konten aus einem Gate tragen `utm_medium = gate` und `utm_campaign = <placement>` in `app_user`; eine mitgebrachte `utm_source` (etwa firstev) bleibt stehen. Auswertung: `SELECT utm_source, utm_campaign, count(*) FROM app_user WHERE utm_medium = 'gate' GROUP BY 1, 2`.
