/**
 * Logik der Story-Karte "Wo du lädst": Orte, Preisklassen, Zeitraum, Fahrtlinien, Auswahl.
 * Rein und ohne Vue, die Komponenten unter components/story/places/ lesen nur hieraus.
 *
 * Ein Ort ist der gespeicherte Geohash (öffentlich 7 Zeichen, rund 150 m, privat 6, rund 600 m).
 * Der Server fasst die Ladungen je Ort zusammen, hier wird nur verglichen, sortiert und benannt.
 */
import type { BenchmarkCard, ChargingBenchmark } from '../api/chargingBenchmarkService'
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

/** home = Zuhause, low/usual/high gegen den günstigsten gepflegten Tarif dort, none = ohne Tarif. */
export type PlaceClass = 'home' | 'low' | 'usual' | 'high' | 'none'
export type PlacePeriod = 'month' | '12m' | 'all'

/** Ab so viel über dem günstigsten gepflegten Tarif gilt ein Preis als teurer. */
export const PRICE_BAND = 0.1

/** Der günstigste gepflegte Tarif an diesem Betreiber, null ohne Tarif. */
export function cheapestCard(benchmark: ChargingBenchmark | null | undefined): BenchmarkCard | null {
  if (!benchmark?.cards.length) return null
  return benchmark.cards.reduce((a, b) => (b.pricePerKwh < a.pricePerKwh ? b : a))
}

/**
 * Der günstigste Tarif ohne Grundgebühr: für Orte über beliebige Zeiträume ehrlich, ob sich ein Abo lohnt,
 * hängt von der Menge ab (das rechnen Moment und Feed mit der Gebühr).
 */
export function cheapestFreeCard(benchmark: ChargingBenchmark | null | undefined): BenchmarkCard | null {
  return cheapestCard(benchmark && { ...benchmark, cards: benchmark.cards.filter(c => c.monthlyFeeEur <= 0) })
}

/** high: ein Tarif ohne Grundgebühr ist mindestens PRICE_BAND günstiger, low: du zahlst höchstens dessen Preis. */
export function placeClass(place: ChargingPlace, benchmark: ChargingBenchmark | null | undefined): PlaceClass {
  if (place.kind === 'HOME') return 'home'
  const cheapest = cheapestFreeCard(benchmark)
  if (place.kind !== 'PUBLIC' || place.pricePerKwh == null || !cheapest) return 'none'
  // Auf Promille gerundet: 0,429 gegen 0,39 ist genau +10 %, nicht 9,9999 %.
  const diff = Math.round((place.pricePerKwh / cheapest.pricePerKwh - 1) * 1000) / 1000
  if (diff >= PRICE_BAND) return 'high'
  if (diff <= 0) return 'low'
  return 'usual'
}

/** Schlüssel des Vergleichs eines Orts, null ohne Vergleich (nicht öffentlich, Betreiber oder Ladeart fehlt). */
export function benchmarkKey(place: ChargingPlace): string | null {
  if (place.kind !== 'PUBLIC' || !place.cpoName?.trim() || !place.chargingType) return null
  return `${place.cpoName}|${place.chargingType}`
}

/** Betreiber, bei dem ein gepflegter Tarif dir am meisten gespart hätte. */
export interface OperatorGap {
  /** Betreibername aus dem Vergleich, nicht der Säulenname. */
  cpoName: string
  chargingType: 'AC' | 'DC'
  /** Dein Preis, nach kWh gewichtet über alle Orte des Betreibers. */
  pricePerKwh: number
  /** Ersparnis mit `card` bei deinen kWh dort, Grundgebühr abgezogen. */
  extraEur: number
  card: BenchmarkCard
  benchmark: ChargingBenchmark
}

/** Unter 2 € Ersparnis lohnt kein Kartenwechsel, wie beim Moment. */
const MIN_OPERATOR_SAVING_EUR = 2

/**
 * Der öffentliche Betreiber (je Ladeart), bei dem der beste gepflegte Tarif bei deinen kWh dort am meisten
 * gespart hätte. Orte desselben Betreibers zählen zusammen.
 */
export function operatorGap(places: ChargingPlace[], benchmarks: Record<string, ChargingBenchmark | null>): OperatorGap | null {
  const sums = new Map<string, { kwh: number; eur: number; benchmark: ChargingBenchmark }>()
  for (const p of places) {
    const key = benchmarkKey(p)
    const benchmark = key ? benchmarks[key] : null
    if (!key || !benchmark || p.pricePerKwh == null || p.kwh <= 0) continue
    // Der Server bildet den Betreiber aus dem Namen: verschiedene Säulennamen landen beim selben Vergleich.
    const op = `${benchmark.cpoName}|${benchmark.chargingType}`
    const sum = sums.get(op) ?? { kwh: 0, eur: 0, benchmark }
    sum.kwh += p.kwh
    sum.eur += p.pricePerKwh * p.kwh
    sums.set(op, sum)
  }
  let best: OperatorGap | null = null
  for (const { kwh, eur, benchmark } of sums.values()) {
    const price = eur / kwh
    const found = bestCard(benchmark.cards, price, kwh)
    if (!found || (best && found.saved <= best.extraEur)) continue
    best = { cpoName: benchmark.cpoName, chargingType: benchmark.chargingType, pricePerKwh: price, extraEur: found.saved, card: found.card, benchmark }
  }
  return best
}

/** Größte Ersparnis nach Grundgebühr, mindestens MIN_OPERATOR_SAVING_EUR. */
function bestCard(cards: BenchmarkCard[], price: number, kwh: number): { card: BenchmarkCard; saved: number } | null {
  let best: { card: BenchmarkCard; saved: number } | null = null
  for (const card of cards) {
    const saved = (price - card.pricePerKwh) * kwh - card.monthlyFeeEur
    if (saved >= MIN_OPERATOR_SAVING_EUR && (!best || saved > best.saved)) best = { card, saved }
  }
  return best
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

export interface PlacesTeaser {
  /** high = an n Orten teurer, low = an n Orten günstiger, plain = Orte und Euro */
  key: 'high' | 'low' | 'plain'
  places: number
  /** Zahl der teuren bzw. günstigen Orte */
  count: number
  eur: number
  /** Beispiel für die Zahl im Teaser: der Ort der Klasse mit dem meisten Geld */
  focus: { place: ChargingPlace; benchmark: ChargingBenchmark } | null
}

/**
 * Satzteile des Teasers zur Ladekarte. Er sagt nur, was der Vergleich belegt: teurer vor günstiger,
 * ohne Vergleich nur Orte und Geld. Kein "nicht mehr als andere", Orte ohne Vergleich wären darin mitgemeint.
 */
export function teaserParts(rows: PlaceRow[]): PlacesTeaser | null {
  if (!rows.length) return null
  const eur = rows.reduce((sum, r) => sum + r.place.costEur, 0)
  for (const key of ['high', 'low'] as const) {
    const hits = rows.filter(r => r.cls === key && r.benchmark)
    if (!hits.length) continue
    const top = hits.reduce((a, b) => (b.place.costEur > a.place.costEur ? b : a))
    return { key, places: rows.length, count: hits.length, eur, focus: { place: top.place, benchmark: top.benchmark! } }
  }
  return { key: 'plain', places: rows.length, count: 0, eur, focus: null }
}

/** Ein Ort mit seiner Preisklasse und dem Vergleich dahinter, so wie Liste, Karte und Teaser ihn zeigen. */
export interface PlaceRow {
  place: ChargingPlace
  cls: PlaceClass
  benchmark: ChargingBenchmark | null
}

/** Orte sortiert, je mit Preisklasse und Vergleich (Schlüssel benchmarkKey). */
export function toPlaceRows(places: ChargingPlace[], benchmarks: Record<string, ChargingBenchmark | null>): PlaceRow[] {
  return sortPlaces(places).map(p => {
    const key = benchmarkKey(p)
    const benchmark = key ? benchmarks[key] ?? null : null
    return { place: p, cls: placeClass(p, benchmark), benchmark }
  })
}
