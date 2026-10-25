# Leaderboard & Ticker

Monatliches Community-Ranking mit taeglich aktualisiertem Rang-Delta und einem scrollenden Ticker-Banner.

---

## Kategorien

| Key | Anzeigename | Einheit | Besser = |
|---|---|---|---|
| `MONTHLY_KWH` | Meiste kWh | kWh | hoeher |
| `MONTHLY_CHARGES` | Meiste Ladevorgaenge | Ladevorgaenge | hoeher |
| `MONTHLY_DISTANCE` | Laengste Strecke | km | hoeher |
| `MONTHLY_COINS` | Meisten Watt | Watt | hoeher |
| `MONTHLY_CHEAPEST` | Guenstigster Lader | ct/kWh | niedriger |
| `MONTHLY_NIGHT_OWL` | Nacht-Eule | Nacht-Ladungen | hoeher |
| `MONTHLY_ICE_CHARGER` | Eisbaer | degC | niedriger |
| `MONTHLY_POWER_CHARGER` | Schnellster Lader | kW | hoeher |

Die ersten fuenf Kategorien (`hasMonthEndReward=true`) gewaehren Monatsende-Bonus-Watt.

`MONTHLY_CHARGES` zaehlt Ladegruppen wie der `/logs`-Feed: zeitlich aufeinanderfolgende Logs eines Autos mit identischem, nicht-leerem Kilometerstand zaehlen als **ein** Ladevorgang (z.B. ab- und wieder einstecken zuhause beim selben km-Stand). Logs ohne Kilometerstand zaehlen einzeln und unterbrechen eine Gruppe.

`MONTHLY_NIGHT_OWL` zaehlt dieselben Ladegruppen, aber nur Logs im Nachtfenster (`logged_at` zwischen 22:00 und 06:00 Uhr). Dadurch sind die beiden Ticker-Werte "Meiste Ladevorgaenge" und "Nacht-Eule" direkt vergleichbar - beide zaehlen Ladevorgaenge, nicht Roh-Logs.

---

## Rang-Delta

Jede Kategorie wird zweimal berechnet: einmal bis heute Mitternacht, einmal bis gestern Mitternacht (gleicher Monat). Die Differenz ergibt das Delta:
- Pfeil nach oben = Rang verbessert
- Pfeil nach unten = Rang verschlechtert
- `NEU` = User war gestern noch nicht im Ranking
- `-` = unveraendert oder erster Tag des Monats

---

## Darstellung

- Kategorien als Chip-Leiste: in der App auf dem Handy fest über der Bottom-Nav (Daumenreichweite), seitlich scrollbar, der gewählte Chip rückt ins Bild; oben nennt ein Titel die gewählte Kategorie. Ab 768 px und auf der Landing steht die Leiste über der Liste, ab 768 px umbrechend und zentriert. Darunter die Beschreibung der gewählten Kategorie.
- Kategorie-Icons tragen ihre Kategoriefarbe, der eigene Platz ist in den Top 10 grau hinterlegt mit "(Du)", außerhalb der Top 10 unten angestellt.
- Rang als neutrale Plakette: Platz 1 dunkel gefüllt, Platz 2 und 3 mit Kante, ab Platz 4 nur die Zahl. Keine Farbcodes, alles in Grau nach App-CI (`docs/design/ci.md`).
- Dieselbe Komponente läuft in der App (`/leaderboard`) und als Top 5 auf der Landing Page.

---

## Top 10 + eigener Rang

Die API gibt immer die Top 10 zurueck. Ist der authentifizierte User nicht in den Top 10, wird sein eigener Eintrag zusaetzlich als `ownEntry` mitgeliefert - angezeigt als eigene Karte mit Kante und "Du"-Label unter der Liste.

---

## Monatsende-Rewards

Am 1. des Monats, 00:05 Uhr (Cron-Job `AppScheduler`), erhalten die Top 3 aller Kategorien mit `hasMonthEndReward=true` Bonus-Watt:

- Platz 1: **100 Watt**
- Platz 2: **50 Watt**
- Platz 3: **25 Watt**

Die Vergabe ist idempotent: `action_description` enthaelt Kategorie + Periode, sodass kein User doppelt belohnt werden kann.

---

## Datenfiltierung

Das Leaderboard beruecksichtigt nur:
- Logs mit `include_in_statistics = true`
- User mit `is_seed_data = false`
- User mit `leaderboard_visible = true`

---

## Leaderboard-Sichtbarkeit (Opt-out)

User koennen sich unter Einstellungen -> Bestenliste vom Ranking abmelden. Der Toggle ruft `PUT /api/users/me/leaderboard-visible?visible=false` auf und setzt `leaderboard_visible = false` in der DB. Der User erscheint dann weder in der Top-10-Liste noch als `ownEntry`.

---

## Ticker-Banner

Ein Band unterhalb der Navigationsleiste (`LeaderboardTicker.vue`), nur für eingeloggte Nutzer in DE/AT/CH, nicht im Demo-Modus. Es zeigt einen Eintrag zur Zeit und wechselt nach einer Verweildauer, überlange Texte laufen einmal durch. Antippen des Texts zeigt den nächsten Eintrag, Wischen blättert, ein Pause-Knopf hält die Rotation an.

### Eintragstypen

| Typ | Inhalt | Farbe | Ziel-Link |
|---|---|---|---|
| LEADER | Führende jeder Kategorie im laufenden Monat | Gelb | `/leaderboard` |
| STAT | Community-Summen des Monats (kWh, CO2, Haushalte, Solar, Wind, Ersparnis, Ladezeit, Heimquote, Top-Anbieter) und "Heute"-Ladungen | Smaragd | keiner |
| PERSONAL | Eigene Daten des Nutzers | Himmelblau | `/dashboard` bzw. `/leaderboard` |

Einträge mit echter Zielseite bekommen rechts neben dem Text einen eigenen Pfeil-Link. Community-Statistiken haben keine Seite und bleiben ohne Link.

### Persönliche Einträge

Höchstens drei, an zweiter, sechster und zehnter Stelle eingemischt:

- **Monat** (`my_month`): kWh, Anzahl Ladevorgänge und Anteil zu Hause im laufenden Monat, für das Auto mit den meisten Ladevorgängen im Monat.
- **Rang** (`my_standing`): bester Rang über alle Kategorien als Position wie in der Bestenliste, mit dem eigenen Wert in einem eigenen Satz je Kategorie, z. B. "Dein schnellster Ladevorgang im Oktober: 48,3 kW - damit bist du #46 in der Rangliste". Nur wenn der Nutzer in Bestenlisten erscheint.
- **Verbrauch** (`my_consumption`): eigener Verbrauch im Monat gegen den Schnitt der Fahrer desselben Modells, aus dem Peer-Vergleich des Dashboards. Nur wenn der Vergleichswert aus dem laufenden Monat stammt und mindestens drei andere Nutzer dahinterstehen.

### "Heute"-Einträge

Bis zu zwei öffentliche Ladungen anderer Nutzer vom heutigen Tag (Berliner Kalendertag), höchstens eine je Nutzer: "Heute hat jemand 42 kWh bei Ionity für 16,38 € geladen (39 ct/kWh)". Ohne Name, Auto, Ort und Uhrzeit.

- Nur belegt öffentliche Ladungen mit Preis, 1-150 kWh und 0,10-1,50 €/kWh.
- Der Anbietername erscheint nur, wenn mindestens drei verschiedene Nutzer ihn verwenden, sonst die Variante ohne Anbieter.
- Ausgeschlossen: Seed-Nutzer, gelöschte Autos und Logs, Nutzer mit abgeschaltetem Schalter "Ladungen anonym im Ticker zeigen" (Einstellungen, Bereich Bestenliste, Standard an).
- Nur über den JWT-Endpoint, erscheint also nicht auf der Landing Page.

### Caches

| Inhalt | Cache | Laufzeit |
|---|---|---|
| Community-Liste | `leaderboard` | 4 h |
| Rankings je Kategorie und Zeitfenster (Bestenliste, eigene Standings, Rang im Ticker) | `leaderboardRanking` | 15 min |
| "Heute"-Einträge | `tickerToday` | 15 min |
| Persönliche Einträge | `tickerPersonal` je Nutzer | 1 h |

Die Monatsend-Prämien rechnen ihr Ranking immer frisch, ohne Cache. Das Frontend lädt die drei Teile parallel. Fällt ein Teil aus, fehlt nur dieser.

### Einklappen

Eine Lasche unter dem Band klappt ein und aus. Beim Einklappen speichert `localStorage` (`ticker-collapsed`) den Zeitpunkt. Nach 7 Tagen klappt der Ticker einmal wieder auf. Ein Altwert `true` ohne Zeitpunkt zählt ab dem ersten Laden.

### Plausible

`ticker_toggle`, `ticker_pause` und `ticker_tap` mit Typ und gegebenenfalls Ziel, nie mit Werten.

---

## API

| Endpoint | Auth | Beschreibung |
|---|---|---|
| `GET /api/public/leaderboard/{category}` | Optional JWT | Gibt Top 10 + optionalen `ownEntry` zurueck |
| `GET /api/public/leaderboard/ticker` | Nein | Community-Ticker-Items (auch Landing Page) |
| `GET /api/ticker/me` | JWT | Persönliche Ticker-Einträge |
| `GET /api/ticker/today` | JWT | "Heute"-Ladungen |
| `PUT /api/users/me/leaderboard-visible?visible=` | JWT | Setzt Leaderboard-Sichtbarkeit |
| `PUT /api/users/me/ticker-share-charges?enabled=` | JWT | Schalter für "Heute"-Einträge |

---

## Externe Witze (JokeAPI.dev)

`ExternalJokeService` laed beim Start (`ApplicationReadyEvent`) und taeglich um 03:00 Uhr 10 deutsche Witze von JokeAPI.dev. Bei Netzwerkfehler: leere Liste, keine Exception.

URL-Parameter: `lang=de&safe-mode&blacklistFlags=nsfw,religious,political,racist,sexist,explicit&type=single&amount=10`

---

## Geplante Weiterentwicklung: Community Achievements

**Status:** Konzept (geplant fuer ~April 2026)

**Problem mit dem aktuellen Ansatz:** Individuelle Rankings mit Coin-Rewards setzen einen Anreiz,
falsche Daten einzutragen. Opt-out hilft nicht, weil Schummler sich nicht austragen.
Fake-Daten korrumpieren ausserdem die Community-Statistiken, aus denen das Leaderboard befuellt wird.

**Richtung:** Vom individuellen Wettbewerb zu kollektiven Achievements umbauen.
Kein Gewinner, kein "Erster Platz" - stattdessen gemeinsame Meilensteine.
Fake-Daten in diesem Kontext sind sinnlos (der Community-Counter wird minimal groesser, kein persoenlicher Gewinn).

### Ideen fuer Community-Achievements

**Kollektive Zahlen mit Wow-Faktor (Referenzwerte):**
- "Diesen Monat zusammen X MWh geladen - entspricht X Haushaltsjahren Stromverbrauch"
- "Zusammen X km elektrisch gefahren - X mal um die Erde"
- "X Tonnen CO2 gegenueber Verbrenner-Aequivalent vermieden"
- "X Euro Kraftstoffkosten gespart (vs. Verbrenner-Schnitt)"

**Community vs. WLTP (Kern-Insight der App):**
- "Community-Effizienz: 18.4 kWh/100km - 3.7% unter WLTP-Durchschnitt"
- Kein Ranking noetig, aber echter Mehrwert gegenueber anderen Quellen

**Monatsziel mit Fortschrittsbalken:**
- "Ziel: 50 MWh im Maerz - ihr seid bei 38.2 MWh (76%)"
- Ziel berechnet sich automatisch aus Vormonats-Durchschnitt
- Jeder Ladevorgang traegt bei - gemeinsam statt gegeneinander

**Fun-Stats ohne Sieger:**
- "X verschiedene Ladenetze genutzt"
- "X verschiedene Fahrzeugmodelle in der Community"
- "Spaeteste Ladung des Monats: 04:23 Uhr" (kein Name, nur Uhrzeit)

### Was mit dem aktuellen Leaderboard passiert

- Coin-Rewards (`hasMonthEndReward`) werden entfernt - kein finanzieller Anreiz mehr
- Individuelles Ranking wird abgeloest oder stark zurueckgedraengt
- Ticker-Banner bleibt, zeigt stattdessen Community-Achievement-Daten
- `leaderboard_visible` Flag in DB kann wiederverwendet werden (oder entfaellt)
