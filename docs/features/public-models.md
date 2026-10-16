# Public Models Pages

Die öffentlichen Modell-Seiten sind die wichtigsten SEO-Seiten der App und zeigen Community-Verbrauchsdaten für alle Elektroautos.

## Personas und Stories (verbindlich für alle Modell-Seiten)

Festgelegt 14.10.2026. Kurzfassung und Begriffs-Tabelle stehen in `private/CLAUDE.md`. Begriffe: "Liste", "Rangliste", "Modellübersichtsseite", "Kaufhilfe" = `/modelle`; "Markenseite" = `/modelle/:brand`; "Modellseite", "Modelldetailseite", "Modell-Seite" = `/modelle/:brand/:model`. Der Fahrprofil-Rechner und das Gate gehören zur Liste und zur Modellseite.

Eine Persona, eine Frage: **"Ich will ein E-Auto kaufen und suche das passende für mich."** Sie hat zwei Ausprägungen.

**Einsteiger (größere, wichtigere Gruppe):** hatte noch nie ein E-Auto und weiß nicht, was kWh sind. Er hat gehört, dass E-Autos weniger weit fahren und man öfter Pause machen muss. Er überschätzt die Reichweitenangst und weiß nicht, wie günstig E-Fahren ist. Ladekarten kennt er nicht. Ob eine eigene Wallbox für ihn in Frage kommt, weiß er nicht.

1. Ich will wissen, welches E-Auto in meinen Alltag passt (wie oft laden, wie viele Pausen auf meiner langen Fahrt), ohne kWh verstehen zu müssen.
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
- **Gemeinsame Skala, ein Balken:** Jede Zeile zeigt einen Balken mit dem Fahrer-Ø (grün unter der WLTP-Angabe, orange darüber) und einem dunklen Strich an der WLTP-Angabe, darunter „Fahrer **20,0** · WLTP 14,6 kWh/100 km“. Alle Balken laufen von null bis zum selben Maximum (95. Perzentil aller Modelle, 1-2-5-10-Schritt, in der Einheit des Markts), damit Modelle untereinander vergleichbar bleiben. Unter „Winterfest“ nennt die Zeile zusätzlich den Mehrverbrauch im Winter.
- **Sortierungen:** Sparsamste, Größte Reichweite, Nah an WLTP (Betrag der Abweichung), Winterfest (Mehrverbrauch Winter gegenüber Sommer), Meiste Daten. Modelle ohne Wert stehen immer am Ende.
- **Kapitel 2 „Was die Fahrer messen“:** ein Kasten (wie Kapitel 1) mit H2 und Untertitel „Verbrauch, Kosten und Reichweite aus echten Ladevorgängen, für alle frei.“ Darin Suche über Marke und Modell (alle Suchbegriffe müssen passen), Sortier-Chips, Klassen-Chips (am Handy waagerecht scrollbar) und der Zähler „8 von 61 Modellen mit Fahrerdaten“, der immer steht (gefiltert mit „Filter aufheben“). Spaltenköpfe kleben nicht. Unter der Liste der Textlink „Worauf die Kosten beruhen“ (öffnet das Annahmen-Sheet). „Noch ohne Fahrerdaten“ steht unter dem Kasten.
- **Erst 8 Zeilen:** danach „Alle 61 Modelle“. Die übrigen Zeilen sind nur per CSS eingeklappt, alle Zeilen und ihre Links bleiben im HTML (SEO). Suche, Klasse, Filter-Chip und ein Sortierwechsel zeigen immer alle Treffer.
- **Filter-Chip (eingeloggt, mit Urlaubsfahrt):** „Schafft meine 600 km mit 1 Stopp“ und „Alle“. Die Zahl der Stopps sind die wenigsten Stopps über alle Modelle (`fewestTripStops`), der Chip zeigt genau die Modelle, die damit auskommen. Gäste sehen den Chip nicht. Plausible `ranking_fit_filter` (Prop `stops`).
- **Chips im Button-Stil** (`ChoiceChip`): dunkle Outline, kleiner harter Schatten, gewählt eingedrückt und dunkel gefüllt, `aria-pressed`. Gilt für Sortierung, Klassen, Filter-Chip, die Marken-Links unten (ohne gewählten Zustand) und „Was ist dir am wichtigsten?“.
- **Strompreis:** Schieberegler mit Voreinstellungen Zuhause und Öffentlich (Community-Ø). Er bestimmt die Kosten pro 100 km. Der Wert liegt im localStorage (`ev-price-per-kwh`) und gilt auch für die Kartenliste.
- **Zeile:** Rang, Vorschaubild (48 px, Lupe öffnet das große Foto mit Bildnachweis in der `ImageLightbox`; ohne Foto das Markenkürzel), Name einmal („Tesla Model 3“, als `<a href>` zur Modellseite) mit „Mittelklasse · 42 Fahrer · 6.627 Ladevorgänge“, der Verbrauchsbalken, die Kostenzelle je 100 km („Strom 5,93 €“, „Benziner 14,62 €“, „Fahrer zahlen Ø 29,6 ct/kWh“, feste Breite) und die Reichweite („343 km“, darunter „Autobahn ohne Stopp bis 310, 1 Stopp bis 550“). Am Desktop am Ende „+ Vergleich“ und der Pfeil. Die ganze Zeile klappt auf und zu. Am Handy: Bild und Name links, Reichweite rechts, darunter Balken links und Kosten rechts; „Zum Vergleich“ steht in der offenen Zeile.
- **Offene Zeile:**
  1. **Für dich** (nur eingeloggt): drei schlichte Kacheln Laden fürs Pendeln (ein Satz zum Winterwert, gilt das ganze Jahr), Urlaubsfahrt {km} (Stopps, Reisezeit), Kosten im Monat (Monats-km, Jahresersparnis gegen den Benziner fürs Pendeln). Darunter klein die Kenner-Zeile: „Autobahn 2 Stopps bis 790 km · Winter +10 % Verbrauch · 23 % über WLTP · Akku 75 und 82 kWh“, jeder Teil nur mit Wert. Gäste sehen keine Kacheln und kein Gate in der Zeile: das eine Gate sitzt in Kapitel 1.
  2. **Was die Fahrer messen:** ein Absatz „Verbrauch 20,0 kWh/100 km (Sommer 19,0, Winter 22,0), WLTP 14,6 kWh/100 km · Reichweite 343 km, im Winter 200 bis 307 km · Ø Ladepreis der Fahrer 29,6 ct/kWh · 6.627 Ladevorgänge von 43 Autos“. Stammen die Werte von einem Fahrer, steht der Hinweis darüber. Ohne „Für dich“ folgt die Kenner-Zeile hier.
  3. **Weiter:** Knopf „Modellseite {Modell}“ mit der Zeile, was nur dort steht; Plausible `ranking_model_page`. Am Handy daneben „Zum Vergleich“. Darunter der Bildnachweis des Zeilenfotos.
- **Vergleich:** Bis zu drei Modelle wählen, die Leiste unten führt auf `/modelle/vergleich?models=...`.
- **Desktop:** ab 1024 px eine Zeile je Modell mit allen Spalten; darunter Handy-Raster. Kein WLTP-Jargon in der eingeklappten Zeile außer der Verbrauchszeile, die Liter je Klasse stehen im Annahmen-Sheet.

### Kaufhilfe

- **Datenlage je Zeile:** "{Klasse} · {n} Fahrer · {m} Ladevorgänge" unter dem Namen. Stammt der Wert von genau einem Fahrer, steht ein Info-Icon daneben ("Wert eines einzelnen Fahrers, kann vom Durchschnitt abweichen"); die offene Zeile nennt Ladevorgänge und Autos. Unter der Liste steht "Mehr Fahrer, belastbarerer Wert." Keine Mindestmenge an Daten, die Rangliste zeigt alle Modelle mit Logs.
- **Noch ohne Fahrerdaten:** Eingeklappter Abschnitt unter der Rangliste mit allen Modellen, die eine WLTP-Spec, aber keine Ladevorgänge haben (Name, WLTP-Wert, Link zur Modellseite, "noch nicht bewertbar") und einem Aufruf zum Eintragen (Registrierung, eingeloggt "Fahrzeug anlegen"). Suche und Klasse gelten für beide Listen. Trifft die Suche nur dort, klappt der Abschnitt auf und der Leerzustand sagt "{Modell} hat noch keine Fahrerdaten, WLTP {x}". Diese Modelle stehen auch in der Liste A bis Z und im JSON-LD `ItemList`.
- **Kapitel 1 "Welches E-Auto passt zu dir?"** (`ModelsAnswerCard` aus `DrivingProfileCard`, `ProfileQuestions`, `AnswerGrid`/`AnswerCell`): grüne Kachel über die volle Breite zwischen Hero und Liste. Fragen: die fünf Regler der Modellseite (`useProfileSliders`, gleiche Labels, Bereiche, Formate und Profilzeile) und "Was ist dir am wichtigsten?" mit "Weit kommen" (wenigste Stopps auf der Urlaubsfahrt, bei Gleichstand größere Winterreichweite) und "Niedrige Kosten" (günstigste im Monat). "Kurze Ladepausen" (kürzeste Reisezeit, nur Modelle mit Wert) erscheint von selbst, sobald mindestens 80 % der Modelle der Rangliste eine Reisezeit haben (`BREAKS_MIN_SHARE`). Die Wahl liegt mit den Antworten im localStorage `ev-needs-check-v2` (`priority`) und geht nicht an den Server.
- **Antworten als Spannen, frei für alle:** vier Sätze im 2×2-Raster wie auf der Modellseite, jeweils 10. bis 90. Perzentil über alle Modelle der Ranglisten-Antwort (`rankingAnswers.ts`): Reichweite im Winter und Sommer (darunter "Deinen Arbeitsweg von 40 km schafft jedes der 61 Modelle" oder die Zahl der Modelle, die es schaffen), Laden fürs Pendeln im Winter ("alle 4 bis 8 Tage", ab drei Wochen in Wochen), Ladestopps auf der Urlaubsfahrt mit der Zahl der Modelle, die mit den wenigsten Stopps auskommen, Kosten im Monat mit Strompreis und Benziner-Vergleich (7,0 l, Benzinpreis aus `/charging-prices`, Ersparnis als Spanne, nur wo das E-Auto günstiger ist). Jede Antwort erscheint erst, wenn ihre Fragen beantwortet sind; die Reichweite immer. Darunter "Worauf das Ergebnis beruht" aufklappbar.
- **Gate:** Gäste sehen unter den Antworten "Welche 5 Modelle passen am besten zu dir?" mit der Brücke aus der freien Antwort ("27 Modelle schaffen deine 600 km mit 1 Stopp. Wir zeigen dir die 5 besten ...") und "Meine 5 Modelle zeigen", darunter Nutzer-Satz und Widerruf. Der Knopf öffnet das `InlineGate` (`placement` `modelle:top5`). Am Handy erscheint die Leiste "Meine 5 Modelle zeigen", solange das Gate aus dem Bild ist; sind Modelle zum Vergleich gewählt, weicht sie der Vergleichsleiste. Die Sperre ist bewusst nur im Frontend: die Zahlen stehen in der öffentlichen Antwort.
- **Freigeschaltet:** Profil als eine Zeile ("15.000 km im Jahr · ... · wichtig: Weit kommen", "Angaben ändern" klappt die Regler aus), als letzte Zeile des Rasters Wallbox-Frage und Regler "Dein Strompreis" (`ProfileTune`, wie auf der Modellseite), darunter "Deine 5 besten Modelle" als Tabelle (`ModelTopFive`): Modell, dann die Spalte der Wichtigkeit (markiert), dann die beiden anderen (Urlaubsfahrt mit Stopps und Reisezeit, Laden fürs Pendeln mit Winterwert, Monat). Am Handy steht der Name über die volle Breite, die drei Werte darunter, der Kopf einmal oben. Jede Zeile verlinkt per `<a href>` auf die Modellseite. Wer mit Antworten ankommt, sieht die Regler eingeklappt (Gäste nur am Handy); beim Schieben klappt nichts weg.
- **Zahlen je Modell vom Server:** Spannen und Top 5 kommen aus `POST /api/public/personal-range/ranking`, derselben Rechnung wie die Modellseite (`PersonalRangeCalculator`) auf dem größten WLTP-Akku. Die Liste fragt beim Laden und 300 ms nach jeder Änderung, mit dem Strompreis der Liste; eine Antwort zu einem älteren Profil wird verworfen. Die eingeklappte Zeile der Liste zeigt keine persönlichen Zahlen; die aufgeklappte Zeile zeigt eingeloggt "Für dich".
- **Einstiegskarten im Hero:** "Welches E-Auto passt zu mir?" springt zu Kapitel 1 (`#passt-zu-dir`), "Welches verbraucht am wenigsten?" zur Liste (`#fahrer-messen`).
- **Ein Strompreis:** Antworten, Top 5 und die Spalte "Kosten je 100 km" rechnen mit demselben Preis: eigener Preis aus dem Regler, sonst der Schnitt aller Fahrer zu Hause (mit Wallbox) oder unterwegs (ohne), aus `/api/public/charging-prices`, auf ein Zehntel Cent. Was die Fahrer des Modells im Schnitt zahlen, steht in der Kostenzelle darunter ("Fahrer zahlen Ø 29,6 ct/kWh").
- **Sortierte Spalte:** der Kopf der sortierten Spalte ist grün, ihr Wert in der Zeile fett (Sparsamste, Nah an WLTP, Winterfest: Verbrauch; Sparsamste mit „Kosten“ im Sheet: Kosten; Größte Reichweite: Reichweite; Meiste Daten: Modell).
- **Modellbilder:** Wikimedia-Commons-Fotos (900 px) in `public/model-images/`, Attribution je Eintrag in `config/modelImages.ts`. Die Rangliste lädt die 240-px-Kopie aus `thumbs/` (rund 12 KB statt 150 KB je Zeile) als 48-px-Vorschau, die Lupe öffnet das 900-px-Bild mit Bildnachweis; `scripts/model-image-thumbs.py` erzeugt sie, ein Vitest prüft, dass beide Dateien je Eintrag vorhanden sind.
- **Kosten gegen Verbrenner:** Der Strompreis ist ein Mischpreis aus Preis zuhause und unterwegs, gewichtet mit dem Anteil zuhause (100 Prozent bei „Laden zuhause ja“, 0 bei „nein“, im Sheet änderbar), also derselbe Preis wie in Kapitel 1. Beide Preise sind mit dem Community-Ø vorbelegt, ein alter Wert aus `ev-price-per-kwh` wird einmalig als Preis zuhause übernommen. Jede Zeile zeigt den absoluten Verbrenner-Preis je 100 km in der Kostenzelle, eingeloggt in der offenen Zeile die Ersparnis pro Jahr fürs Pendeln (Pendel-km am Tag × Tage × 46 Wochen, `COMMUTE_WEEKS_PER_YEAR`; ohne beantwortetes Pendeln keine Jahreszahl). Ohne Antwort zu Laden zuhause rechnet der Mischpreis wie mit Laden zuhause. Verbrenner-Verbrauch je Fahrzeugklasse (`COMBUSTION_LITERS_BY_CLASS` in `costMix.ts`, Benzin/Diesel: Kleinwagen 5,8/4,6, Kompakt 6,8/5,2, Mittelklasse 7,6/6,0, SUV 8,4/6,6, Großer SUV 10,5/8,0, Oberklasse 9,8/7,4, Sportwagen 10,5/8,0, Van 8,5/6,8, Pickup 11,0/9,0; unbekannte Klasse 7,5/6,0). Werte sind Mittelwerte veröffentlichter Realverbrauchs-Spannen (Spritmonitor-Community, EU-OBFCM rund 20 Prozent über WLTP); Sportwagen, Van und Pickup sind redaktionelle Schätzungen. Ein im Sheet eingetragener Literwert gilt für alle Klassen, leer heißt wieder je Klasse. Dieselbe Tabelle (Benzin) liegt im Backend auf `VehicleCategory` und speist den Monatsrückblick; die Konstante 7,0 l (`FuelPriceService`) gilt noch für Ticker und Ladepreis-Referenz. Kraftstoffpreis aus Tankerkönig nur in Deutschland vorbelegt, sonst leer mit Hinweis. Vergleich nur in metrischen Märkten (DE, EN, NO, SE); GB und US sehen keinen Verbrenner-Block.
- **Annahmen-Sheet:** Der Textlink „Worauf die Kosten beruhen“ unter der Liste öffnet ein Bottom-Sheet (ab 640 px zentrierter Dialog) mit Anteil zuhause, zwei Strompreisen, Benziner/Diesel, l/100 km (leer = je Fahrzeugklasse, die Liste je Klasse steht darunter), Preis je Liter, der Wahl „Große Zahl je Modell: Verbrauch oder Kosten pro 100 km“ (hebt unter „Sparsamste“ die gewählte Spalte hervor), „Angaben zurücksetzen“ und dem Satz, dass nichts an EV Monitor gesendet wird. Fokus bleibt im Dialog, Escape schließt, der Fokus kehrt zum Link zurück. Alle Angaben liegen im localStorage `ev-cost-assumptions-v2`.
- **Stopp-Leiter:** Reichweite auf der Autobahn ohne Stopp, mit einem und mit zwei Schnellladestopps auf dem größten Akku, vom Server aus derselben Regel wie die Stopps der Urlaubsfahrt (`LongTripPlan.reachKm`): Autobahnverbrauch, erste Etappe 100 auf 10 %, je Stopp 10 auf 80 %, auf 10 km gerundet. Modelldaten, unabhängig von den Antworten, darum auch ohne Antwort sichtbar. Steht in der Reichweitenzelle als „Autobahn ohne Stopp bis 270, 1 Stopp bis 510“ (Desktop), 2 Stopps in der Kenner-Zeile der offenen Zeile. Die Schnellladeleistung (`fastChargePowerKw`, 75. Perzentil kurzer DC-Sessions, `FastChargePowerTable`) bestimmt die Minuten je Stopp in der Reisezeit, nicht die km.
- **Sortierung bleibt:** Der gewählte Sortier-Chip liegt im localStorage `ev-ranking-sort` und gilt beim nächsten Besuch wieder. Die Sortier-Chips ordnen die Liste; "Was ist dir am wichtigsten?" ordnet nur die Top 5.
- **Plausible-Events:** `ranking_sort` (Wert), `needs_check_edit` (entprellt 1 s), `assumptions_open`, `compare_start` (Anzahl Modelle), `ranking_entry_card` (Prop `card`), `ranking_priority` (Prop `value`), `ranking_gate_shown`, `ranking_unlocked` (Prop `models`), `ranking_top5_click` (Prop `rank`), `ranking_fit_filter` (Prop `stops`), `personal_range_wallbox`. Goals im Plausible-Dashboard manuell anlegen.
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

Links "Sag uns, wie du fährst." mit fünf Schiebereglern (`ProfileSlider`), rechts die Ergebnis-Box (ab 768 px klebend). Am Handy steht die Box im Fluss nach dem Fahrprofil. Dort gleitet unten eine schmale Leiste "Deine Antworten freischalten" herein (`UnlockBar`, über der Bottom-Nav für Angemeldete), sobald die Überschrift "Sag uns, wie du fährst." über ihr steht, die Box einmal im Bild war oder ein Regler bewegt wurde. Sie weicht, solange der Freischalt-Knopf der Box (bzw. die Anmeldung) im Bild ist, und verschwindet mit den Antworten. Details in `personal-range-calculator.md`. Regler und Antworten sind dieselben Bausteine wie Kapitel 1 der Liste (`ProfileQuestions`, `AnswerGrid`, `AnswerCell`, Profilzeile aus `useProfileSliders`); die Kosten rechnen mit demselben Strompreis wie die Liste (Schnitt aller Fahrer zu Hause oder unterwegs), nicht mit dem Preis der Fahrer dieses Modells.

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
