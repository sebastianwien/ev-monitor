# Public Models Pages

Die öffentlichen Modell-Seiten sind die wichtigsten SEO-Seiten der App und zeigen Community-Verbrauchsdaten für alle Elektroautos.

## Personas und Stories (verbindlich für alle Modell-Seiten)

Festgelegt 14.10.2026. Gilt für `/modelle`, `/modelle/:brand`, `/modelle/:brand/:model`, den Fahrprofil-Rechner und das Gate. Kurzfassung steht in `private/CLAUDE.md`.

Eine Persona, eine Frage: **"Ich will ein E-Auto kaufen und suche das passende für mich."** Sie hat zwei Ausprägungen.

**Einsteiger (größere, wichtigere Gruppe):** hatte noch nie ein E-Auto und weiß nicht, was kWh sind. Er hat gehört, dass E-Autos weniger weit fahren und man öfter Pause machen muss. Er überschätzt die Reichweitenangst und weiß nicht, wie günstig E-Fahren ist. Ladekarten kennt er nicht. Ob eine eigene Wallbox für ihn in Frage kommt, weiß er nicht.

1. Ich will wissen, ob ein E-Auto in meinen Alltag passt (wie oft laden, wie viele Pausen auf meiner langen Fahrt), ohne kWh verstehen zu müssen.
2. Ich will sehen, was es mich im Monat kostet, am besten im Vergleich zu meinem Verbrenner.
3. Ich will die Modelle finden, die das schaffen.

**Kenner:** weiß, was kWh sind, und kennt die saisonalen Unterschiede.

4. Ich will Modelle nach echten Fahrerdaten vergleichen (Verbrauch gegen WLTP, Winter, Datenbasis).
5. **TODO, später, noch nicht beauftragt:** Ich will mein aktuelles E-Auto (bei EV Monitor erfasst) mit anderen Modellen vergleichen, zum Beispiel einen 4 Jahre alten Wagen gegen ein neueres Modell. Möglicher Quick-Win: das eigene Auto gegen Modelle anderer Hersteller stellen. Braucht ein eigenes Konzept.

**Was daraus folgt** (Schlüsse, nicht gemessen):

- Die Fragen der Einsteiger sind genau die persönlichen Ergebnisse (wie oft laden, Stopps, Monatskosten). Die freien Messwerte (kWh/100 km, "Nah an WLTP") sprechen eher die Kenner an. Jedes Gate-Konzept muss sich dazu verhalten, dass das Schloss sonst auf dem Inhalt der Hauptgruppe sitzt.
- Sprache zuerst in Euro, Pausen, Tagen und Kilometern. kWh und WLTP bleiben für Kenner aufklappbar und stehen für SEO im HTML.
- Laden zu Hause oder unterwegs (Wallbox, Ladekarte) ändert die Monatskosten stark und ist für Einsteiger eine Unbekannte. Die Wallbox-Frage wird deshalb erst nach dem Freischalten gestellt, vorher rechnet der Server mit Laden zu Hause.

## Routen

| Route | Beschreibung |
|---|---|
| `/modelle` | Übersichtsseite mit Filter, Modell-Grid und Marken-Grid |
| `/modelle/:brand` | Alle Modelle einer Marke mit WLTP-Daten |
| `/modelle/:brand/:model` | Detailseite als Story in vier Kapiteln mit persönlichem Rechner |
| `/rankings` | Community-Rankings (sparsamste Modelle) |

## Fahrzeugkategorien

Alle Modelle sind einer von 9 Fahrzeugkategorien zugeordnet:

| Enum | Anzeige |
|---|---|
| `CITY_CAR` | Kleinwagen |
| `COMPACT` | Kompakt |
| `SEDAN` | Mittelklasse |
| `SUV` | SUV |
| `LARGE_SUV` | Großer SUV |
| `LUXURY` | Oberklasse |
| `SPORTS` | Sportwagen |
| `VAN` | Van |
| `PICKUP` | Pickup |

Die Kategorie ist im `CarModel`-Enum direkt gespeichert (kein DB-Lookup). Zugriff via `CarModel.getCategory()` und `CarModel.byCategory(VehicleCategory)`.

## Filter auf /modelle

Zwei kombinierbare Client-seitige Filter:
- **Markenfilter** (Multi-Select Dropdown)
- **Kategoriefilter** (Single-Select Dropdown)

Beide Filter zusammen ergeben `AND`-Verknüpfung.

## Rangliste auf /modelle (Vorschau)

Mit `?ansicht=neu` zeigt `/modelle` (und jede Marktvariante wie `/us/models`) statt der Kartenliste eine Rangliste. Die Vorschau ist `noindex, nofollow`, ohne Parameter bleibt die Kartenliste die Standardansicht.

- **Hero ohne Karte:** Zeile "Community-Daten, Stand {Monat}", einfarbige Überschrift, Lede, darunter die vier Kennzahlen über die volle Breite. Keine Legende in der Filterspalte.
- **Gemeinsame Skala als Balkenpaar:** Jede Zeile zeigt zwei beschriftete Balken, „Hersteller 13,9“ (WLTP, grau) und „Fahrer 11,6“ (Community-Ø, grün unter der Angabe, orange darüber) mit der Abweichung „−17 %“ am Fahrer-Balken. Zahl und Abweichung stehen im Balken (bei kurzen Balken dahinter). Alle Balken laufen von null bis zum selben Maximum (95. Perzentil aller Modelle, 1-2-5-10-Schritt, in der Einheit des Markts), damit Modelle untereinander vergleichbar bleiben; Ausreißer enden am Rand. Keine Ticks, keine Legende: die Worte und die Zahlen am Balkenende tragen die Aussage „Prospekt gegen Alltag“.
- **Sortierungen:** Sparsamste, Größte Reichweite, Nah an WLTP (Betrag der Abweichung), Winterfest (Mehrverbrauch Winter gegenüber Sommer), Meiste Daten. Modelle ohne Wert stehen immer am Ende.
- **Filter:** Suche über Marke und Modell (alle Suchbegriffe müssen passen) und Fahrzeugklasse.
- **Strompreis:** Schieberegler mit Voreinstellungen Zuhause und Öffentlich (Community-Ø). Er bestimmt die Kosten pro 100 km. Der Wert liegt im localStorage (`ev-price-per-kwh`) und gilt auch für die Kartenliste.
- **Offene Zeile:** Eingeklappt ist Vorschau, ausgeklappt das vollständige Datenblatt (Doppelungen zur Zeile sind gewollt). Ohne Einrückung, volle Breite mit derselben rechten Kante wie die Zeile, kein großes Foto. Drei Teile:
  1. **Für dich:** dieselben Fragen wie die Modellseite als ein Block in deren Kartensprache (Handy untereinander, ab 768 px nebeneinander), je beantworteter Teil eine Karte: Laden fürs Pendeln (Sommer groß, Winter darunter), Urlaubsfahrt {km} (Stopps groß, Reisezeit darunter), Kosten im Monat (mit Monats-km und Jahresersparnis gegen den Benziner fürs Pendeln). Ohne Antwort entfällt der Block. Gäste sehen dieselben Karten mit verschwommenem festem Platzhalter und Schloss und darunter ein Gate: "Deine Antworten für den {Modell}: wie oft du laden musst, wie viele Stopps auf {km} km und was es im Monat kostet.", ein Knopf „Kostenlos freischalten“ (öffnet das Gate in der Karte über der Liste) und der Satz zur Mail ("Kein Passwort: mit Google oder einem Code per Mail. Damit entsteht ein kostenloses Konto, das du jederzeit löschst.").
  2. **Was die Fahrer messen:** vier kompakte Gruppen mit Zeilen Label/Wert statt Kacheln, ab 768 px zwei Spalten. Verbrauch (Real Ø mit Spanne je Akku-Variante, WLTP mit Abweichung, Sommer/Winter/WLTP-Balken), Reichweite (dieselbe Zahl wie die Spalte: 100 auf 10 % mit dem Ø-Verbrauch der Fahrer, kleiner bis großer Akku; die beste Variante mit eigenem Verbrauch nur als Zusatz; darunter die Stopp-Leiter "Autobahn ab vollem Akku": ohne Stopp, 1 Stopp, 2 Stopps), Kosten (je 100 km "bei {Preis}, dein Mischpreis aus zu Hause und unterwegs" mit Link „Annahmen ändern“, Ersparnis gegen Benziner je 100 km, "Ø Ladepreis der Fahrer: was Fahrer dieses Modells im Schnitt zahlen"), Datenbasis (Ladevorgänge, Fahrer, Autos). Stammen die Werte von einem Fahrer, steht der Hinweis über den Gruppen.
  3. **Weiter:** ein Knopf „Modellseite {Modell}“ mit der Zeile, was nur dort steht (Akku-Varianten, Bewertungen, Belege, häufige Fragen); Plausible `ranking_model_page`. Darunter der Bildnachweis des Zeilenfotos als Link zur Quelle. „Zum Vergleich“ steht nur in der Zeile: am Desktop am Ende der Hinweiszeile, am Handy als Band unten auf dem Foto.
- **Vergleich:** Bis zu drei Modelle wählen, die Leiste unten führt auf `/modelle/vergleich?models=...`.
- **Mobil:** Sortier-Chips und Achse bleiben unter der Navigation stehen. Ab 1024 px Filterspalte links, das Balkenpaar neben dem Namen, dann die Kostenzelle („Strom 3,22 €“, darunter „Benziner 12,47 €“) und ab 1280 px die Reichweite: großer Akku als Zahl („298 km“), darunter die Stopp-Leiter (siehe unten). Kein WLTP-Jargon in der Tabelle, die Liter je Klasse stehen im Detail und im Annahmen-Sheet.

### Kaufhilfe

- **Datenlage je Zeile:** "{m} Ladevorgänge · {n} Fahrer" (mobil unter dem Namen, Desktop Spalte "Daten"). Stammt der Wert von genau einem Fahrer, steht ein Info-Icon daneben ("Wert eines einzelnen Fahrers, kann vom Durchschnitt abweichen"); im aufgeklappten Detail nennt die Datenbasis Fahrer und Autos. Unter der Liste steht "Mehr Fahrer, belastbarerer Wert." Keine Mindestmenge an Daten, die Rangliste zeigt alle Modelle mit Logs.
- **Noch ohne Fahrerdaten:** Eingeklappter Abschnitt unter der Rangliste mit allen Modellen, die eine WLTP-Spec, aber keine Ladevorgänge haben (Name, WLTP-Wert, Link zur Modellseite, "noch nicht bewertbar") und einem Aufruf zum Eintragen (Registrierung, eingeloggt "Fahrzeug anlegen"). Suche und Klasse gelten für beide Listen. Trifft die Suche nur dort, klappt der Abschnitt auf und der Leerzustand sagt "{Modell} hat noch keine Fahrerdaten, WLTP {x}". Diese Modelle stehen auch in der Liste A bis Z und im JSON-LD `ItemList`.
- **Bedarfs-Check über den Zeilen:** Block "Welches E-Auto passt zu dir?" am Kopf der Tafel mit den fünf Reglern der Modellseite (`ProfileSlider`, gleiche Labels, Bereiche und Formate): Kilometer pro Jahr, Davon Autobahn (km), Arbeitsweg pro Tag, Pendeltage pro Woche (0 bis 6), Längste Urlaubsfahrt (bis 2.000 km). Unbewegt steht "noch offen". Am Handy klappen schon vorhandene Antworten (z. B. von der Modellseite oder einem früheren Besuch) die Regler zu einer Zeile zusammen ("17.000 km im Jahr · 4.000 km Autobahn · 20 km Arbeitsweg, 2 Tage · 700 km Urlaubsfahrt · Ändern"), "Regler einklappen" klappt nach eigenen Antworten zu, "Ändern" im Klebekopf öffnet sie. Mobil untereinander, ab 640 px zwei, ab 1024 px drei Spalten, ab 1280 px alle fünf in einer Reihe in kompakter Form (Label über dem Wert, ohne Min/Max-Zeile). Darunter: ohne Antwort "Schieb die Regler, dann bewerten wir jedes Modell für dich."; mit Urlaubsfahrt "So viele Modelle schaffen deine {l} km:" und Stopp-Chips im Stil der Sortier-Chips (am Handy seitlich scrollend), höchstens fünf, ab einem Chip vor der ersten Zahl über null bis zum ersten, den alle Modelle erreichen (`MAX_STOPS` 10); Chips mit 0 gesperrt. Angemeldet filtert ein Chip die Liste und setzt die Stopp-Grenze für den Punkt je Zeile, nochmal tippen oder "Alle zeigen" hebt den Filter auf. Als Gast öffnet ein Chip das Gate ("Deine 24 Modelle für 700 km sind bereit.") und filtert nach dem Code. Gäste sehen nach der ersten Antwort "Für jedes Modell sagen wir dir:" mit drei Kacheln (Wie oft laden?, Stopps auf {l} km, Kosten im Monat), je mit festem verschwommenem Platzhalter und Schloss, daneben bzw. am Handy darunter "Kostenlos freischalten". Eingeloggt steht nach Arbeitsweg und Tagen "Hast du zu Hause eine Wallbox?" Ja/Nein mit "Beeinflusst Kosten und Ladehäufigkeit."; bis zur Antwort rechnet die Liste wie die Modellseite mit Laden zu Hause. Alle Antworten liegen im localStorage `ev-needs-check-v2` (inkl. `annualKm`, `highwayKm`) und gelten auf beiden Seiten als beantwortet. Für die Zahlen je Zeile gehen sie ohne Personenbezug an `POST /api/public/personal-range/ranking` (siehe unten). Der Punkt je Zeile urteilt nur über die Urlaubsfahrt: grün, wenn der größte Akku sie mit der gewählten Stopp-Grenze schafft (anfangs ohne Stopp), sonst orange, grau für ein Modell ohne Zahlen vom Server (`meetsNeeds`).
- **Mail-Gate für die Zahlen je Zeile:** Modellwerte (Verbrauch, Kosten je 100 km, Reichweite, Stopp-Leiter) und die Anzahl je Stopp-Chip sind frei. "Kosten im Monat" kommt vom Server wie auf der Modellseite (Jahreskilometer ÷ 12, sonst Arbeitsweg × Pendeltage × 4,4 Wochen, Community-Preis AC bzw. DC je nach Wallbox); sie und die Jahresersparnis gegen den Benziner sehen nur Eingeloggte. Der Hinweis je Zeile mit Laderhythmus und Stopp-Zahl erscheint erst eingeloggt; Gäste sehen den Punkt und ein Urteil ohne Zahl ("400 km ohne Ladestopp" / "1.150 km mit Ladestopp", daneben Stopps und Zeit als verschwommener Platzhalter mit Schloss) und, mit beantwortetem Pendeln, die Zeile „Fürs Pendeln“ mit verschwommenem Platzhalter und Schloss. Der Platzhalter ist ein fester Text, nie der echte Wert. „Kostenlos freischalten“ öffnet das `InlineGate` in der Alltag-Antwort (Google One Tap oder Mail-Code, `placement` `modelle:rangliste`), eine offene Zeile verweist mit demselben Knopf dorthin. Nach dem Code zeigen alle Zeilen ihre Zahlen ohne Neuladen. Die vier Antworten teilen sich den Speicher `ev-needs-check-v2` mit der Modellseite. Plausible: `ranking_gate_shown`, `ranking_unlocked` (Prop `models`). Unter dem Namen steht je beantworteter Teil eine Zeile: Pendeln, Urlaubsfahrt, Kosten im Monat; der Punkt steht nur an der Urlaubszeile, die anderen halten seinen Platz frei. Als Gast folgen alle drei Zeilen einem Muster: Label, verschwommener Platzhalter, Schloss. Die Sperre ist bewusst nur im Frontend: die Zahlen stehen in der öffentlichen Antwort, sie lassen sich ohnehin aus den freien Modelldaten ableiten; das Gate ist ein Anreiz, kein Schutz.
- **Bedarfs-Hinweis je Zeile (eingeloggt):** Alle Zahlen kommen aus derselben Rechnung wie die Modellseite (`PersonalRangeCalculator`) auf dem größten WLTP-Akku, keine Formel im Browser. Die Liste fragt `POST /api/public/personal-range/ranking` beim Laden und 300 ms nach jeder geänderten Antwort; die alten Zahlen bleiben stehen, bis die neuen da sind, eine Antwort zu einem älteren Profil wird verworfen, ohne Antwort zeigen die Zeilen nur Modelldaten. Offene Fragen gehen mit dem Startwert des Reglers hinaus (wie auf der Modellseite), Jahreskilometer nur, wenn beantwortet; die Zeile zeigt einen Teil nur, wenn er beantwortet ist. Pendeln: "Fürs Pendeln alle 14 Tage laden, im Winter alle 10 Tage" aus `chargesPerWeekSummer`/`Winter`, Worte aus `chargeRhythm.ts` wie auf der Modellseite. Urlaubsfahrt: "400 km: 1 Stopp, 4 h 5 min unterwegs" (Stopps und Reisezeit aus `LongTripPlan`, Autobahnverbrauch, Start 100 %, je Stopp 10 auf 80 %). Ein Modell, das der Server auslässt (ohne WLTP-Spec), zeigt "noch nicht bewertbar". Mobil zwei Zeilen mit Ellipse, im Detail vollständig.
- **Klebekopf über den Zeilen:** Sortier-Chips plus „Annahmen“, darunter Spaltenköpfe (Desktop). Die Basiszeile „Für Pendeln 40 km an 3 Tagen, 400 km längste Fahrt, Laden zuhause · Ändern“ nennt nur beantwortete Fragen, fehlt ohne Antwort ganz und erscheint erst, wenn der Bedarfs-Check aus dem Bild gescrollt ist (IntersectionObserver), sonst würde sie die Felder darüber wiederholen. Der Zähler „{n} von {m} Modellen · Filter aufheben“ steht nur, wenn Suche, Klasse oder Bedarfsfilter die Liste kürzen; eine Sortier-Erklärung gibt es nicht mehr, stattdessen ist der Kopf der sortierten Spalte grün (Sparsamste, Nah an WLTP, Winterfest: Verbrauch; Größte Reichweite: Reichweite; Meiste Daten: Modell).
- **Bedarfs-Check, Eingabe:** Die Felder „am Tag“ und „längste Fahrt“ zeigen jede Ziffer sofort, die Liste und die Kennzahlen folgen 300 ms nach der letzten Ziffer oder sofort bei Blur und Enter (`useDebouncedCommit`); ein geleertes Feld ist wieder unbeantwortet, nicht null. Die Buttons Tage und Zuhause wirken ohne Verzögerung.
- Die Überschrift des Bedarfs-Checks steht in 20 px, ab lg 24 px.
- **Modellbilder:** Wikimedia-Commons-Fotos (900 px) in `public/model-images/`, Attribution je Eintrag in `config/modelImages.ts`. Die Rangliste lädt die 240-px-Kopie aus `thumbs/` (rund 12 KB statt 150 KB je Zeile); `scripts/model-image-thumbs.py` erzeugt sie, ein Vitest prüft, dass beide Dateien je Eintrag vorhanden sind.
- **Kosten gegen Verbrenner:** Der Strompreis ist ein Mischpreis aus Preis zuhause und unterwegs, gewichtet mit dem Anteil zuhause (80 Prozent bei „Laden zuhause ja“, 0 bei „nein“, im Sheet änderbar). Beide Preise sind mit dem Community-Ø vorbelegt, ein alter Wert aus `ev-price-per-kwh` wird einmalig als Preis zuhause übernommen. Jede Zeile zeigt den absoluten Verbrenner-Preis je 100 km (mobil in der Meta-Zeile, Desktop als Vergleichszeile der Kostenzelle), im Detail die Differenz pro 100 km und pro Jahr fürs Pendeln (Pendel-km am Tag × Tage × 46 Wochen, `COMMUTE_WEEKS_PER_YEAR`; ohne beantwortetes Pendeln keine Jahreszahl). Ohne Antwort zu Laden zuhause rechnet der Mischpreis wie mit Laden zuhause. Verbrenner-Verbrauch je Fahrzeugklasse (`COMBUSTION_LITERS_BY_CLASS` in `costMix.ts`, Benzin/Diesel: Kleinwagen 5,8/4,6, Kompakt 6,8/5,2, Mittelklasse 7,6/6,0, SUV 8,4/6,6, Großer SUV 10,5/8,0, Oberklasse 9,8/7,4, Sportwagen 10,5/8,0, Van 8,5/6,8, Pickup 11,0/9,0; unbekannte Klasse 7,5/6,0). Werte sind Mittelwerte veröffentlichter Realverbrauchs-Spannen (Spritmonitor-Community, EU-OBFCM rund 20 Prozent über WLTP); Sportwagen, Van und Pickup sind redaktionelle Schätzungen. Ein im Sheet eingetragener Literwert gilt für alle Klassen, leer heißt wieder je Klasse. Dieselbe Tabelle (Benzin) liegt im Backend auf `VehicleCategory` und speist den Monatsrückblick; die Konstante 7,0 l (`FuelPriceService`) gilt noch für Ticker und Ladepreis-Referenz. Kraftstoffpreis aus Tankerkönig nur in Deutschland vorbelegt, sonst leer mit Hinweis. Vergleich nur in metrischen Märkten (DE, EN, NO, SE); GB und US sehen keinen Verbrenner-Block.
- **Annahmen-Sheet:** Chip „Annahmen“ in der Sortierleiste öffnet ein Bottom-Sheet (ab 640 px zentrierter Dialog) mit Anteil zuhause, zwei Strompreisen, Benziner/Diesel, l/100 km (leer = je Fahrzeugklasse, die Liste je Klasse steht darunter), Preis je Liter, der Wahl „Große Zahl je Modell: Verbrauch oder Kosten pro 100 km“ (gilt unter „Sparsamste“), „Angaben zurücksetzen“ und dem Satz, dass nichts an EV Monitor gesendet wird. Fokus bleibt im Dialog, Escape schließt, der Fokus kehrt zum Chip zurück. Alle Angaben liegen im localStorage `ev-cost-assumptions-v2`.
- **Stopp-Leiter:** Reichweite auf der Autobahn ohne Stopp, mit einem und mit zwei Schnellladestopps auf dem größten Akku, vom Server aus derselben Regel wie die Stopps der Urlaubsfahrt (`LongTripPlan.reachKm`): Autobahnverbrauch, erste Etappe 100 auf 10 %, je Stopp 10 auf 80 %, auf 10 km gerundet. Modelldaten, unabhängig von den Antworten, darum auch ohne Antwort sichtbar. Steht in der Reichweitenzelle als „Autobahn 270 · 1 Stopp 510 · 2 Stopps 750“ (untereinander, mit „bis“ bei zwei Akkus) und in der offenen Zeile als Leiter „Autobahn ab vollem Akku“. Die Schnellladeleistung (`fastChargePowerKw`, 75. Perzentil kurzer DC-Sessions, `FastChargePowerTable`) bestimmt die Minuten je Stopp in der Reisezeit, nicht die km.
- **Sortierung bleibt:** Der gewählte Sortier-Chip liegt im localStorage `ev-ranking-sort` und gilt beim nächsten Besuch wieder. Eine eigene Prioritätsfrage gibt es nicht mehr, die Sortier-Chips sind die Priorität.
- **Plausible-Events:** `ranking_sort` (Wert), `needs_check_edit` (entprellt 1 s), `assumptions_open`, `compare_start` (Anzahl Modelle). Goals im Plausible-Dashboard manuell anlegen.
- **Annahmen** (Konstanten in `costMix.ts`, im UI genannt): Mischpreis und Verbrennervergleich für die Kosten je 100 km und die Jahresersparnis. Reichweite, Laderhythmus, Stopps und Monatskosten rechnet der Server (`PersonalRangeCalculator`), Reichweite überall 100 → 10 % des Netto-Akkus (`RangeWindow.SHARE` 0,9).

## API-Endpunkte

| Endpoint | Beschreibung |
|---|---|
| `GET /api/public/models/top?limit=N` | Top-N Modelle nach Log-Anzahl (max. 200) inkl. `category`, `categoryDisplayName`, WLTP-Mittel über die Spec-Varianten und Sommer-/Winterverbrauch (Sommer Mai bis August, Winter November bis Februar, aus `EvLogStatisticsService`). Dazu `contributorCount` und `carCount` (Fahrer und Autos hinter `logCount`), `minNetCapacityKwh`/`maxNetCapacityKwh` (kleinster und größter Netto-Akku der WLTP-Specs) sowie `typicalRangeMinKm`/`typicalRangeMaxKm` (Netto-Kapazität mal 100 durch Ø-Verbrauch) und `winterRangeMinKm`/`winterRangeMaxKm` (mit Winterverbrauch, sonst null). Die Platzhalter-Marke "Andere Marke" erscheint nie. Der Cache-Warmup wärmt `limit=200` für den anonymen und den Seed-Pfad. |
| `GET /api/public/charging-prices` | Community-Ø Strompreis zuhause und öffentlich (EUR/kWh) plus `petrolPricePerLiter`, `dieselPricePerLiter` (Tankerkönig-Tagesschnitt Deutschland, ohne API-Key 2,15 / 2,32) und `combustionLitersPer100km` (7,0; das Frontend nutzt stattdessen die Liter je Klasse). |
| `GET /api/public/models/without-data` | Modelle mit WLTP-Spec, aber ohne Ladevorgänge: Namen, Slug, Kategorie, WLTP min/Ø/max, kleinste und größte Netto-Kapazität. Ohne "Andere Marke", 1 h gecacht, öffentlich. |
| `GET /api/public/models/:brand/:model` | Detail-Stats inkl. `category` + `categoryDisplayName` |
| `GET /api/public/rankings/categories` | Alle 9 Kategorien als `[{ key, displayName }]` |
| `GET /api/public/rankings/efficiency` | Sparsamste Modelle (min. 10 Logs, gültige Consumption) |

## Rankings

Die `/rankings`-Seite zeigt sparsamste Modelle mit Kategorie-Tabs zum Filtern. Modelle ohne `avgConsumptionKwhPer100km` oder mit weniger als 10 Logs werden ausgeschlossen.

## Detailseite "Prospekt gegen Realität"

## Navigation für Eingeloggte

Auf `/modelle`, den Marken- und den Modellseiten (de und en, Router-Meta `publicNav`) sehen auch Eingeloggte die öffentliche Kopfzeile statt der lila App-Navigation, des Tickers und des App-Footers: Logo, Sprache, Theme, rechts das Watt-Guthaben und ein grüner "Dashboard"-Knopf an der Stelle von "Registrieren". Die Bottom-Nav am Handy bleibt. Alle anderen Routen behalten die App-Navigation.

Die Modellseite zeigt in fünf Zonen mit fester Reihenfolge, was die Community misst, und führt zum persönlichen Ergebnis. Alle Modellzahlen sind frei, hinter dem Gate liegen nur die persönlichen Antworten (siehe `personal-range-calculator.md`). Jede Zone ist eine Box (`ModelBox`). Kanten-Stil wie im App-Header: am Handy tragen die Karten im Inhalt eine schwarze 2-px-Kontur mit 2 px hartem Schatten (`edge-card`), ab 768 px die großen Boxen dieselbe Kontur mit 3 px Schatten (`edge-box`); beide Utilities in `index.css`, dunkel mit heller Kontur.

### 1 Hero

- H1 "{Modell} - Verbrauch und Reichweite", Breadcrumb am Handy nur mit Haus-Icon.
- Variante als zweizeiliger Knopf "Variante wählen" über "{Variante}, {kWh}" (`VariantPicker`): ab 768 px ein Popover, am Handy ein Bottom-Sheet, das von unten hereingleitet ("Welcher Akku?", je Akku Reichweite und Prospektwert). Voreingestellt ist die Variante mit den meisten gemessenen Fahrten. Alle Reichweiten der Seite rechnen mit dem Netto-Akku der gewählten Variante, die Verbrauchswerte (Mittel, Saison, Streckentyp) sind modellweit wie in der Rangliste.
- Hero-Satz, ein Satz je Zeile: "Der Prospekt sagt {WLTP × 0,9} km." / "{Fahrer} Fahrer sagen {gemessen} km." (zweite Zeile grün). Der Prospektwert steht auf demselben Akkufenster wie die Fahrer (100 auf 10 %, auf 10 km gerundet, 434 → 390 km). Darunter ab 768 px "Beide Werte von 100 auf 10 % Akku.", dann "Je nach Fahrer sind es {min} bis {max}." (sparsamster bis durstigster Fahrer) und "Wie weit du kommst, hängt von Strecke, Tempo und Jahreszeit ab." Unter 5 Autos spricht der Satz von ersten Messungen. Ohne Logs: "{WLTP} km verspricht {Marke}. Noch kein Fahrer hat nachgemessen." Zahl und Einheit sind fest verbunden.
- Tacho (`RangeGauge`): Hersteller als Spur, Jahresschnitt als äußerer Bogen, Winter auf eigenem inneren Ring, Legende "100 % → 10 %" mit Akku-Symbolen. Am Handy zwischen Hero-Satz und Basissatz, ab 768 px rechts.
- Angedockt unter dem Hero "Was Käufer wissen wollen" (`BuyerQuestions`) mit den vier Fragen der Ergebnis-Box. Ab 768 px steht ein Käufer in der Mitte (Open Peeps, CC0, `public/illustrations/buyer.svg`), zwei Sprechblasen links, zwei rechts, darunter "Für mein Fahrprofil ausrechnen ↓". Am Handy ein Chat im Look eines Messengers: die Fragen links (die Alltagsfrage "Wie oft muss ich laden?" zuerst), rechts die Antwort von EV Monitor "Rechnen wir dir aus ↓". Beide Knöpfe gleiten in 450 ms zum Fahrprofil (`smoothScroll.ts`, bei reduzierter Bewegung ein Sprung).
- Schnellladeleistung, Verbrauchsspanne der Fahrer und Flotten-Winterfaktor kommen aus `GET /api/public/models/{brand}/{model}/story-context`.

### 2 Datenblatt

Titel "Im Alltag gemessen" (ohne Logs "Laut Hersteller"), darunter "Aus {n} Ladevorgängen von {m} Autos". Vier Kacheln (`ModelFactTiles`), mobil 2×2 mit gleich hohen Zeilen, ab 768 px in einer Reihe. Je eine große Zahl mit Einheit, darunter eine Zeile, deren Zahl ebenfalls groß und fett steht, eine Stufe kleiner.

| Kachel | Zahl | Zeile |
|---|---|---|
| Reichweite | Jahresschnitt (90 % Netto-Akku ÷ Verbrauch) | "{km} im Winter", ohne Saisondaten "Prospekt {km}" |
| Verbrauch | Community-Schnitt | "{x} % über Prospekt ({WLTP})" bzw. "unter" |
| Laden | Minuten 10 auf 80 % (Netto × 0,7 ÷ typische DC-Leistung) | "{kW} kW im Schnitt", darunter "10 % → 80 %" mit Akku-Symbolen wie im Tacho |
| Ladekosten | zwei gleich große Zeilen: "{€} zu Hause" (20 auf 80 %, Netto × 0,6 ÷ 0,9 × Heimpreis) und "{€} Schnelllader" (10 auf 80 %, Netto × 0,7 ÷ 0,95 × Schnellladepreis) | - |

Heimpreis: kWh-gewichteter Preis der AC-Ladungen, die nicht als öffentlich markiert sind. Fahrer-Schnellladepreis: kWh-gewichtet über DC-Ladungen ab 50 kW (Spitzenleistung, ohne sie die mittlere Leistung der Ladung); beide erst ab 5 bepreisten Ladungen (`findHomeFastCostStatsByModel`). Die Schnelllader-Zeile rechnet im deutschen Markt mit dem Preis ohne Abo (`fastAdHocPricePerKwh` aus `GET /api/public/charging-prices`, serverseitig 4 h gecacht (`chargingReferencePrices`): je Netz EnBW, IONITY, Fastned der teuerste aktuelle DC-Tarif ohne Monatsgebühr, gemittelt), sonst mit dem Fahrer-Schnellladepreis, sonst mit dem Mittelpreis (`fastChargePrice` in `modelStory.ts`). Fehlt ein Heimpreis, steht nur die Schnelllader-Zeile.

Fehlt ein Messwert, steht "-" mit ehrlicher Zeile ("noch nicht gemessen"); ohne Logs zeigen Reichweite und Verbrauch den Herstellerwert grau mit "laut Hersteller, noch nicht gemessen". Schnellladestopp in Kachel, SEO-Text und FAQ ist derselbe Wert zum selben Preis; SEO-Text und FAQ nennen den Preis dazu.

### 3 Fahrprofil und Ergebnis

Links "Sag uns, wie du fährst." mit fünf Schiebereglern (`ProfileSlider`), rechts die Ergebnis-Box (ab 768 px klebend). Am Handy steht die Box im Fluss nach dem Fahrprofil. Dort gleitet unten eine schmale Leiste "Deine Antworten freischalten" herein (`UnlockBar`, über der Bottom-Nav für Angemeldete), sobald die Überschrift "Sag uns, wie du fährst." über ihr steht, die Box einmal im Bild war oder ein Regler bewegt wurde. Sie weicht, solange der Freischalt-Knopf der Box (bzw. die Anmeldung) im Bild ist, und verschwindet mit den Antworten. Details in `personal-range-calculator.md`.

### 4 Belege "Was Winter und Autobahn beim {Modell} ausmachen"

`ModelEvidence`, sichtbar und nicht eingeklappt (beantwortet "{Modell} Reichweite Winter" und "{Modell} Verbrauch Autobahn"):

- Reichweite nach Jahreszeit: Balken Prospekt (grau), Sommer, Winter auf einer Skala ab 0, darunter "Im Winter kommst du {x} % weniger weit als im Sommer" und die Zahl der Ladevorgänge je Saison. Ohne Saisondaten Prospekt gegen Jahresschnitt und der Flotten-Winteraufschlag.
- Verbrauch nach Strecke: Stadt, Gemischt, Autobahn als Abweichung in Prozent vom Mittel aller Fahrten mit Streckentyp, grüne Balken nach links (sparsamer), orange nach rechts (durstiger), ohne eigene Legende. Absolute Streckenwerte stammen aus einer jüngeren Teilmenge der Fahrten und lägen oft alle unter dem Gesamtschnitt des Datenblatts; die Abweichung widerspricht ihm nicht. Autobahn erst ab 5 Autos, wie im Rechner; unter zwei Zeilen entfällt die Grafik.

### 5 Bewertungen und Mehr Details

Ab 768 px nebeneinander. "Mehr Details" ist eine Liste aufklappbarer Abschnitte (Inhalt steht im DOM und wird indexiert): Baujahre (Histogramm erst ab 3 Baujahren und 10 Autos; am 13.10.2026 erreichten das 11 von 65 Modellen), Alle Varianten (je Akku Prospekt-Reichweite und -Verbrauch, gemessener Verbrauch mit Fahrtenzahl, Sommer/Winter-Reichweite), Häufige Fragen, Ähnliche Modelle (crawlbare Links).

### 6 Halter-Aufruf und SEO-Text

Für Gäste "Du fährst selbst einen {Modell}? Dann wärst du Auto Nummer {n}." mit "Kostenlos mitmachen". Danach der SEO-Text in Prosa.

## Fahrerbewertungen

Unter den Messdaten zeigt der Block "Fahrer sagen" die Bewertungen der Fahrer dieses Modells, ab 5 Bewertungen mit Schnitt und `aggregateRating` im JSON-LD. Details in [Fahrzeugbewertung](vehicle-ratings.md).

## Demo-Mode (Seed Users)

Seed-User sehen alle Seed-Daten in den Community-Aggregationen. Public APIs akzeptieren optional JWT - wenn Seed-User eingeloggt, werden Seed-Daten einbezogen.

## SEO

Alle drei Routen haben vollständiges SEO-Setup:

- **Title** - dynamisch mit echten Zahlen: z.B. `"Tesla Model 3: 15.2 kWh/100km real vs. 14.9 kWh WLTP"`; die Reichweitenspanne im Title rechnet je Variante nach derselben Regel wie der Hero (90 % Netto-Akku ÷ Modell-Schnitt)
- **Meta Description** - dynamisch mit Community-Daten, Log-Anzahl und WLTP-Delta
- **Canonical URL** - korrekt gesetzt pro Route
- **hreflang** - DE/EN Alternates auf allen drei Routen
- **JSON-LD Structured Data:**
  - `/modelle` - `ItemList` + `BreadcrumbList`
  - `/modelle/:brand` - `ItemList` + `BreadcrumbList`
  - `/modelle/:brand/:model` - `WebPage` + `BreadcrumbList` + `FAQPage` (wenn FAQ-Daten vorhanden)
- **Interne Verlinkung** - crawlbar via `<a href>` (kein reines JS-Routing)

**Hinweis:** Das Schema auf der Modell-Detailseite ist `WebPage`, nicht `Article` oder `Product`. `Article` hat GSC-Fehler produziert ("offers/review/aggregateRating fehlt"), `Product` wäre semantisch passend, erfordert aber `aggregateRating` mit Sternebewertungen - die wir nicht haben.

## Partner-Banner

Unter "Andere beliebte Elektroauto-Modelle", vor dem Footer, zeigt die Modellseite Banner von Partnern (zuerst firstev.de). Inhalt in `frontend/src/config/partners.ts` (Ziel-URL, Bild, Alt-Text, Größe, aktiv), Bild lokal unter `frontend/public/partners/`, kein fremdes Script. Inaktive Einträge rendern nichts. Der Link ist folgbar (`rel="noopener"`, kein nofollow), öffnet in neuem Tab und trägt `utm_source=ev-monitor&utm_medium=banner&utm_campaign=modellseite`. Klicks: Plausible-Event `partner_banner_click` mit `partner`.

Banner freischalten: Bild ablegen, Größe eintragen, `active: true`.

## Aktions-Banner

Angemeldete Nutzer ohne Abo mit passendem Aktionscode sehen oben ein Banner (siehe `subscription-tiers.md`, Aktionscodes).
