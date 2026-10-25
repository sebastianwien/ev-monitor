/**
 * Logik der Story-Karte "Wo du lädst": Orte, Preisklassen, Zeitraum, Fahrtlinien, Auswahl.
 * Rein und ohne Vue, die Komponenten unter components/story/places/ lesen nur hieraus.
 *
 * Ein Ort ist der gespeicherte Geohash (öffentlich 7 Zeichen, rund 150 m, privat 6, rund 600 m).
 * Der Server fasst die Ladungen je Ort zusammen, hier wird nur verglichen, sortiert und benannt.
 */
import type { ChargingBenchmark } from '../api/chargingBenchmarkService'
import { tripLine, type TripLine } from '../utils/tripMap'
import { monthWindow } from './storyModel'

export type PlaceKind = 'HOME' | 'PRIVATE' | 'PUBLIC' | 'UNKNOWN'

/** Antwort von GET /api/logs/places. */
export interface ChargingPlace {
  geohash: string
  kind: PlaceKind
  charges: number
  kwh: number
  costEur: number
  /** Geld je kWh der bezahlten Ladungen, null ohne bezahlte Ladung. */
  pricePerKwh: number | null
  lastChargedAt: string
  /** Neueste Ladung des Orts, Ziel von "Betreiber eintragen". */
  lastLogId: string
  cpoName: string | null
  chargingType: 'AC' | 'DC' | null
}

/** home = Zuhause, low/usual/high gegen andere Fahrer, none = ohne Vergleich. */
export type PlaceClass = 'home' | 'low' | 'usual' | 'high' | 'none'
export type PlacePeriod = 'month' | '12m' | 'all'

/** Ab dieser Abweichung vom Median der anderen Fahrer gilt ein Preis als teurer oder günstiger. */
export const PRICE_BAND = 0.1

export function placeClass(place: ChargingPlace, benchmark: ChargingBenchmark | null | undefined): PlaceClass {
  if (place.kind === 'HOME') return 'home'
  if (place.kind !== 'PUBLIC' || place.pricePerKwh == null || !benchmark) return 'none'
  // Auf Promille gerundet: 0,429 gegen 0,39 ist genau +10 %, nicht 9,9999 %.
  const diff = Math.round((place.pricePerKwh / benchmark.medianPricePerKwh - 1) * 1000) / 1000
  if (diff >= PRICE_BAND) return 'high'
  if (diff <= -PRICE_BAND) return 'low'
  return 'usual'
}

/** Schlüssel des Vergleichs eines Orts, null ohne Vergleich (nicht öffentlich, Betreiber oder Ladeart fehlt). */
export function benchmarkKey(place: ChargingPlace): string | null {
  if (place.kind !== 'PUBLIC' || !place.cpoName?.trim() || !place.chargingType) return null
  return `${place.cpoName}|${place.chargingType}`
}

/** Ein Vergleichsaufruf je Paar Betreiber und Ladeart. Der Server bildet den Betreiber aus dem Namen. */
export function benchmarkPairs(places: ChargingPlace[]): Array<{ cpo: string; type: 'AC' | 'DC' }> {
  const seen = new Map<string, { cpo: string; type: 'AC' | 'DC' }>()
  for (const p of places) {
    const key = benchmarkKey(p)
    if (key && !seen.has(key)) seen.set(key, { cpo: p.cpoName!, type: p.chargingType! })
  }
  return [...seen.values()]
}

/** Zuhause zuerst, dann nach Euro absteigend. */
export function sortPlaces(places: ChargingPlace[]): ChargingPlace[] {
  return [...places].sort((a, b) => Number(b.kind === 'HOME') - Number(a.kind === 'HOME') || b.costEur - a.costEur)
}

/**
 * Betreibername ohne Land und Standortzusatz ("Tesla Supercharger Graz, Austria - Webling" wird
 * "Tesla Supercharger Graz"). null für Zuhause und ohne Betreiber, dann benennt die Art den Ort.
 */
export function placeName(place: ChargingPlace): string | null {
  if (place.kind === 'HOME') return null
  const name = place.cpoName?.split(', ')[0].split(' - ')[0].trim()
  return name || null
}

/** Öffentlich ohne Betreiber: ohne ihn gibt es keinen Vergleich, der Nutzer kann ihn eintragen. */
export function needsOperator(place: ChargingPlace): boolean {
  return place.kind === 'PUBLIC' && !place.cpoName?.trim()
}

/** Kreisdurchmesser in px nach Wurzel der kWh, damit ein Vielladeort die Karte nicht zudeckt. */
export function pinSize(kwh: number): number {
  return Math.round(Math.min(34, Math.max(16, 12 + Math.sqrt(Math.max(0, kwh)) * 1.1)))
}

const LEGEND_ORDER: PlaceClass[] = ['low', 'usual', 'none', 'high']

/** Legende: nur Klassen, die vorkommen. Zuhause erklärt sein Haus-Symbol selbst. */
export function legendClasses(classes: PlaceClass[]): PlaceClass[] {
  return LEGEND_ORDER.filter(c => classes.includes(c))
}

/** Teile des Kopfsatzes: "13 Orte in 12 Monaten, an 4 zahlst du mehr als andere." */
export function leadParts(classes: PlaceClass[]): { key: 'empty' | 'plain' | 'high'; places: number; high: number } {
  const high = classes.filter(c => c === 'high').length
  return { key: classes.length === 0 ? 'empty' : high > 0 ? 'high' : 'plain', places: classes.length, high }
}

export interface DayWindow {
  /** ISO-Tag, inklusiv, null = offen. */
  from: string | null
  to: string | null
}

const isoDay = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

/** Monat der Story wie die Kosten-Kachel, 12 Monate wie LAST_12_MONTHS der Statistik, Alles offen. */
export function periodWindow(period: PlacePeriod, month: { year: number; month: number }, today: Date): DayWindow {
  if (period === 'month') {
    const w = monthWindow(month.year, month.month)
    return { from: w.start, to: w.end }
  }
  if (period === '12m') {
    const start = new Date(today.getFullYear(), today.getMonth() - 12, today.getDate())
    return { from: isoDay(start), to: isoDay(today) }
  }
  return { from: null, to: null }
}

/** Fenster für /trips (Zeitpunkte), gleiche Umrechnung wie die Fahrten des Story-Monats. */
export function tripWindowParams(window: DayWindow): { from?: string; to?: string } {
  const params: { from?: string; to?: string } = {}
  if (window.from) params.from = new Date(`${window.from}T00:00:00Z`).toISOString()
  if (window.to) params.to = new Date(new Date(`${window.to}T00:00:00Z`).getTime() + 86_400_000).toISOString()
  return params
}

export interface PlacesTrip {
  id: string
  tracePolyline?: string | null
  routePolyline?: string | null
  routeKind?: string | null
}

/** Nur gefahrene Linien (gematcht oder roh). Die Skizze weiß nichts von der Fahrt und bleibt weg. */
export function drawableTripLines(trips: PlacesTrip[]): Array<{ id: string } & TripLine> {
  return trips.flatMap(t => {
    const line = tripLine(t.tracePolyline, t.routePolyline, t.routeKind)
    return line && line.source !== 'sketch' ? [{ id: t.id, ...line }] : []
  })
}

/**
 * Zoom beim Antippen: nah genug für die Straßen ringsum, nicht genauer als die gespeicherte Zelle
 * (7 Zeichen rund 150 m, 6 rund 600 m, ältere Ladungen mit 5 Zeichen rund 5 km).
 */
export function focusZoom(geohash: string): number {
  if (geohash.length >= 7) return 15
  return geohash.length === 6 ? 14 : 12
}

export interface PlaceSelection<V> {
  active: string | null
  /** Kartenausschnitt vor dem ersten Antippen. */
  saved: V | null
}

/**
 * Antippen von Zeile oder Punkt. Erster Tipp markiert, zoomt hin und merkt den Ausschnitt davor,
 * ein Wechsel behält diesen ursprünglichen Ausschnitt, der zweite Tipp auf dasselbe stellt ihn her.
 */
export function tapPlace<V>(state: PlaceSelection<V>, geohash: string, currentView: V):
  { state: PlaceSelection<V>; focus: string | null; restore: V | null } {
  if (state.active === geohash) return { state: { active: null, saved: null }, focus: null, restore: state.saved }
  return { state: { active: geohash, saved: state.saved ?? currentView }, focus: geohash, restore: null }
}
