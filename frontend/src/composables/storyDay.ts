import geohash from 'ngeohash'
import { tripLine } from '../utils/tripMap'

/**
 * Tagesblatt der Story: was an einem Tag war (Fahrten und Ladungen), als Ablauf und Tagesband.
 * Tagesgrenzen in Ortszeit des Browsers, wie die Tagesleiste (buildDayCoverage).
 */

/** Was das Tagesblatt von einer Fahrt braucht (Auszug aus EvTripResponse). */
export interface DayTrip {
  id: string
  tripStartedAt: string
  tripEndedAt: string
  distanceKm: number | null
  socStart: number | null
  socEnd: number | null
  routePolyline?: string | null
  tracePolyline?: string | null
  routeKind?: string | null
  routeType?: string | null
  locationStartGeohash?: string | null
  locationEndGeohash?: string | null
}

/** Was das Tagesblatt von einer Ladung braucht (Auszug aus EvLogResponse). */
export interface DayLog {
  id: string
  loggedAt: string
  chargeDurationMinutes: number | null
  socBeforeChargePercent: number | null
  socAfterChargePercent: number | null
  kwhCharged: number | null
  kwhAtVehicle: number | null
  costEur: number | null
  priceUnknown?: boolean
  isPublicCharging: boolean | null
  cpoName: string | null
  chargingType: 'AC' | 'DC' | 'UNKNOWN' | null
}

interface EventBase { id: string; from: number; to: number; socFrom: number | null; socTo: number | null }
export type DayEvent =
  | (EventBase & { kind: 'trip'; trip: DayTrip })
  | (EventBase & { kind: 'charge'; log: DayLog })

const DAY_MIN = 1440
/** Halt, bis zu dem zwei Fahrten als eine gelten können. */
export const MERGE_MAX_GAP_MIN = 15
/** Standzeit, ab der sie im Ablauf genannt wird. */
export const STANDING_MIN = 45

const pad = (n: number) => String(n).padStart(2, '0')
const keyOf = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
const dateOf = (key: string) => { const [y, m, d] = key.split('-').map(Number); return new Date(y, m - 1, d) }
const minOfDay = (d: Date) => d.getHours() * 60 + d.getMinutes()

export function dayKeyOf(iso: string | Date): string {
  return keyOf(new Date(iso))
}

/** Ortstag als ISO-Zeitpunkte für /logs und /trips (from und to inklusive). */
export function dayRange(key: string): { from: string; to: string } {
  const start = dateOf(key)
  const next = dateOf(shiftDay(key, 1))
  return { from: start.toISOString(), to: new Date(next.getTime() - 1).toISOString() }
}

export function shiftDay(key: string, n: number): string {
  const d = dateOf(key)
  d.setDate(d.getDate() + n)
  return keyOf(d)
}

/** Einträge eines Tages, zeitlich sortiert. Fahrt über Mitternacht gehört zum Starttag. */
export function eventsOfDay(key: string, trips: DayTrip[], logs: DayLog[]): DayEvent[] {
  const ev: DayEvent[] = []
  for (const t of trips) {
    const start = new Date(t.tripStartedAt)
    if (keyOf(start) !== key) continue
    const from = minOfDay(start)
    const end = new Date(t.tripEndedAt)
    const to = keyOf(end) === key ? Math.max(from + 1, minOfDay(end)) : DAY_MIN
    ev.push({ kind: 'trip', id: t.id, from, to, socFrom: t.socStart, socTo: t.socEnd, trip: t })
  }
  for (const l of logs) {
    const start = new Date(l.loggedAt)
    if (keyOf(start) !== key) continue
    // loggedAt ist der Ladebeginn
    const from = minOfDay(start)
    const to = Math.min(DAY_MIN, from + Math.max(1, l.chargeDurationMinutes ?? 1))
    ev.push({ kind: 'charge', id: l.id, from, to, socFrom: l.socBeforeChargePercent, socTo: l.socAfterChargePercent, log: l })
  }
  return ev.sort((a, b) => a.from - b.from)
}

export const chargeKwh = (l: DayLog): number | null => l.kwhCharged ?? l.kwhAtVehicle

export function daySummary(ev: DayEvent[]) {
  let trips = 0, km = 0, charges = 0, kwh = 0, eur = 0
  for (const e of ev) {
    if (e.kind === 'trip') { trips++; km += e.trip.distanceKm ?? 0 }
    else { charges++; kwh += chargeKwh(e.log) ?? 0; eur += e.log.costEur ?? 0 }
  }
  return { trips, km, charges, kwh, eur }
}

/** Was einer Ladung fehlt. Kosten 0 ist ein Wert, kein Fehlen. */
export function missingOf(l: DayLog): ('price' | 'kwh')[] {
  const m: ('price' | 'kwh')[] = []
  if (l.costEur == null && !l.priceUnknown) m.push('price')
  if (chargeKwh(l) == null) m.push('kwh')
  return m
}

export type ChargePlace = { kind: 'operator'; name: string } | { kind: 'home' | 'public' | 'unknown' }

/** Ort einer Ladung: Betreiber, sonst was gespeichert ist. NULL heißt unbekannt, nicht privat. */
export function chargePlace(l: DayLog): ChargePlace {
  const name = l.cpoName?.replace(/, Germany.*$/, '').replace(/ - .*$/, '').trim()
  if (name) return { kind: 'operator', name }
  if (l.isPublicCharging === false) return { kind: 'home' }
  if (l.isPublicCharging === true) return { kind: 'public' }
  return { kind: 'unknown' }
}

export const gapMinutes = (a: DayEvent, b: DayEvent) => b.from - a.to

/** Zwei aufeinanderfolgende Fahrten ohne Ladung dazwischen und mit kurzem Halt. */
export const canMerge = (a: DayEvent, b: DayEvent) =>
  a.kind === 'trip' && b.kind === 'trip' && gapMinutes(a, b) <= MERGE_MAX_GAP_MIN

export const showStanding = (a: DayEvent, b: DayEvent) => gapMinutes(a, b) >= STANDING_MIN

export interface RibbonLine { kind: 'trip' | 'charge' | 'idle'; x1: number; soc1: number; x2: number; soc2: number }

/**
 * Tagesband über 24 h (x in Minuten): Akkulinie aus gemessenen SoC-Punkten, Standzeit gestrichelt,
 * keine Linie vor dem ersten und nach dem letzten Eintrag. Darunter ein Band je Eintrag.
 */
export function ribbonSegments(ev: DayEvent[]) {
  const lines: RibbonLine[] = []
  const dots: { kind: DayEvent['kind']; x: number; soc: number }[] = []
  let prev: [number, number] | null = null
  for (const e of ev) {
    if (prev && e.socFrom != null) lines.push({ kind: 'idle', x1: prev[0], soc1: prev[1], x2: e.from, soc2: e.socFrom })
    if (e.socFrom != null && e.socTo != null) lines.push({ kind: e.kind, x1: e.from, soc1: e.socFrom, x2: e.to, soc2: e.socTo })
    else if (e.socTo != null) dots.push({ kind: e.kind, x: e.to, soc: e.socTo })
    if (e.socTo != null) prev = [e.to, e.socTo]
  }
  const bands = ev.map(e => ({ kind: e.kind, id: e.id, x: e.from, w: e.to - e.from }))
  const socs = ev.flatMap(e => [e.socFrom, e.socTo]).filter((v): v is number => v != null)
  const first = ev.find(e => e.socFrom != null)?.socFrom ?? socs[0]
  const last = [...ev].reverse().find(e => e.socTo != null)?.socTo ?? socs[socs.length - 1]
  const caption = socs.length ? { first, last, min: Math.min(...socs) } : null
  return { lines, dots, bands, caption }
}

/**
 * Mini-Strecke einer Fahrt als SVG-Pfad im Feld w×h, Norden oben, Seitenverhältnis wie auf der Karte
 * (für die kurze Distanz einer Fahrt reicht das ohne Projektion). Punkte als [lat, lng].
 */
export function routeThumb(points: [number, number][], w: number, h: number, pad = 4): { d: string; end: [number, number] } | null {
  if (points.length < 2) return null
  const lats = points.map(p => p[0]), lngs = points.map(p => p[1])
  const minLat = Math.min(...lats), maxLat = Math.max(...lats), minLng = Math.min(...lngs), maxLng = Math.max(...lngs)
  const spanX = Math.max(maxLng - minLng, 1e-9), spanY = Math.max(maxLat - minLat, 1e-9)
  const s = Math.min((w - 2 * pad) / spanX, (h - 2 * pad) / spanY)
  const ox = (w - (maxLng - minLng) * s) / 2, oy = (h - (maxLat - minLat) * s) / 2
  const xy = points.map(([lat, lng]) => [Math.round((ox + (lng - minLng) * s) * 10) / 10, Math.round((oy + (maxLat - lat) * s) * 10) / 10] as [number, number])
  return { d: xy.map((p, i) => `${i ? 'L' : 'M'}${p[0]},${p[1]}`).join(''), end: xy[xy.length - 1] }
}

/** Nachtragen: Datum des gewählten Tages, Uhrzeit von jetzt (auf die Minute). */
export function dayAtNow(key: string, now: Date = new Date()): Date {
  const d = dateOf(key)
  d.setHours(now.getHours(), now.getMinutes(), 0, 0)
  return d
}

export interface BatteryBar { from: number; soc: number | null; kind: 'charge' | 'trip' | 'idle' }

/**
 * Akku-Balken wie im Akku-Diagramm des Handys: ein Balken je halbe Stunde, Höhe = Akku am Ende
 * des Abschnitts (linear zwischen gemessenen Werten). Vor dem ersten und nach dem letzten
 * Messwert kein Balken, dort ist der Stand unbekannt. Farbe: laden vor fahren vor stehen.
 */
export function batteryBars(ev: DayEvent[], slot = 30): BatteryBar[] {
  const pts: [number, number][] = []
  for (const l of ribbonSegments(ev).lines) pts.push([l.x1, l.soc1], [l.x2, l.soc2])
  for (const d of ribbonSegments(ev).dots) pts.push([d.x, d.soc])
  pts.sort((a, b) => a[0] - b[0])
  const first = pts[0]?.[0], last = pts[pts.length - 1]?.[0]
  const socAt = (m: number) => {
    let prev = pts[0]
    for (const p of pts) {
      if (p[0] >= m) return p[0] === prev[0] ? p[1] : prev[1] + ((p[1] - prev[1]) * (m - prev[0])) / (p[0] - prev[0])
      prev = p
    }
    return prev[1]
  }
  return Array.from({ length: DAY_MIN / slot }, (_, i) => {
    const from = i * slot, to = from + slot
    const known = pts.length > 0 && from < last! && to > first!
    const over = (k: DayEvent['kind']) => ev.some(e => e.kind === k && e.from < to && e.to > from)
    return { from, soc: known ? Math.round(socAt(Math.min(to, last!)) * 10) / 10 : null, kind: over('charge') ? 'charge' : over('trip') ? 'trip' : 'idle' }
  })
}

/** Linie der Mini-Strecke: aufgezeichnete oder berechnete Spur, sonst die Luftlinie zwischen Start und Ziel (gestrichelt). */
export function thumbLine(t: DayTrip): { points: [number, number][]; dashed: boolean } | null {
  const line = tripLine(t.tracePolyline, t.routePolyline, t.routeKind)
  if (line) return { points: line.points, dashed: false }
  if (!t.locationStartGeohash || !t.locationEndGeohash || t.locationStartGeohash === t.locationEndGeohash) return null
  const a = geohash.decode(t.locationStartGeohash), b = geohash.decode(t.locationEndGeohash)
  return { points: [[a.latitude, a.longitude], [b.latitude, b.longitude]], dashed: true }
}

/** Höchster und tiefster Balken für die Beschriftung; an einem flachen Tag nur der höchste. */
export function barExtremes(bars: BatteryBar[]) {
  const known = bars.map((b, index) => ({ index, soc: b.soc })).filter((x): x is { index: number; soc: number } => x.soc != null)
  if (!known.length) return { max: null, min: null }
  const max = known.reduce((a, x) => (x.soc > a.soc ? x : a))
  const min = known.reduce((a, x) => (x.soc < a.soc ? x : a))
  return { max, min: max.soc - min.soc < 1 ? null : min }
}
