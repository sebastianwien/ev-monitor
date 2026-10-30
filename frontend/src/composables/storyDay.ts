import type { ClimateSummary } from '../utils/tripClimate'
import { aggregateGroupCost } from './useChargingEfficiency'

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
  loadExtras?: string[]
  /** Vom Auto gemessen: Zeit, Strecke und Akku nicht änderbar */
  measured?: boolean
  outsideTempCelsius?: number | null
  climate?: ClimateSummary | null
  avgSpeedKmh?: number | null
  maxSpeedKmh?: number | null
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
  chargingProviderId?: string | null
  maxChargingPowerKw?: number | null
}

interface EventBase { id: string; from: number; to: number; socFrom: number | null; socTo: number | null }
export type DayEvent =
  | (EventBase & { kind: 'trip'; trip: DayTrip })
  /** `parts`: Teilladungen eines Bündels, älteste zuerst; `log` ist dann die älteste. */
  | (EventBase & { kind: 'charge'; log: DayLog; parts?: DayLog[] })

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

/**
 * Teilladungen als ein Eintrag (Überschussladen, Nachladen): die Ladegruppen der Liste (`groupKeysOf`), je Tag
 * und erst ab zwei Teilen. Steht etwas dazwischen, etwa eine Fahrt, trennt es das Bündel, damit der Ablauf in
 * seiner Reihenfolge bleibt. Das Akku-Diagramm bekommt weiter die Teile.
 */
export function bundleEvents(events: DayEvent[], groupOf: Map<string, string>): DayEvent[] {
  const out: DayEvent[] = []
  let run: (DayEvent & { kind: 'charge' })[] = []
  const flush = () => {
    if (run.length > 1) {
      const parts = run.map(e => e.log)
      out.push({
        kind: 'charge', id: run[0].id, from: run[0].from, to: Math.max(...run.map(e => e.to)),
        socFrom: run[0].socFrom, socTo: run[run.length - 1].socTo, log: run[0].log, parts,
      })
    } else out.push(...run)
    run = []
  }
  for (const e of events) {
    const key = e.kind === 'charge' ? groupOf.get(e.id) : undefined
    if (run.length && (!key || groupOf.get(run[0].id) !== key)) flush()
    if (key && e.kind === 'charge') run.push(e)
    else out.push(e)
  }
  flush()
  return out
}

export interface BundleTotals {
  costEur: number | null
  kwh: number | null
  /** auf der Abrechnungsbasis wie bei einer Ladung, nur wenn jedes Teil einen Preis hat */
  pricePerKwh: number | null
  /** Teile, denen der Preis fehlt, älteste zuerst */
  priceless: DayLog[]
  chargingType: 'AC' | 'DC' | null
}

/** Summen eines Bündels aus seinen Teilen; Betrag und Preis je kWh wie die Ladegruppe der Liste (aggregateGroupCost). */
export function bundleTotals(parts: DayLog[]): BundleTotals {
  const cost = aggregateGroupCost(parts.map(p => ({ ...p, chargingProviderId: p.chargingProviderId ?? null })))
  const kwhs = parts.map(chargeKwh).filter((k): k is number => k != null)
  const types = new Set(parts.map(p => p.chargingType))
  const [type] = types
  return {
    costEur: cost.totalCostEur,
    kwh: kwhs.length ? kwhs.reduce((a, k) => a + k, 0) : null,
    pricePerKwh: cost.isFullyPriced && cost.costBasisKwhTotal > 0 ? cost.totalCostEur! / cost.costBasisKwhTotal : null,
    priceless: parts.filter(p => missingOf(p).includes('price')),
    chargingType: types.size === 1 && (type === 'AC' || type === 'DC') ? type : null,
  }
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
  const caption = socs.length ? { first, last, min: Math.min(...socs), max: Math.max(...socs) } : null
  return { lines, dots, bands, caption }
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
    const over = (k: DayEvent['kind']) => ev.some(e => e.kind === k && barOfEvent({ from }, e, slot))
    return { from, soc: known ? Math.round(socAt(Math.min(to, last!)) * 10) / 10 : null, kind: over('charge') ? 'charge' : over('trip') ? 'trip' : 'idle' }
  })
}

/** Ob ein Balken des Akku-Diagramms (ab `from`, `slot` Minuten breit) in die Zeit eines Eintrags fällt. */
export function barOfEvent(bar: { from: number }, event: { from: number; to: number }, slot = 30): boolean {
  return bar.from < event.to && bar.from + slot > event.from
}
