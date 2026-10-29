import {
  eventsOfDay, dayKeyOf, shiftDay, daySummary, chargeKwh, type DayEvent, type DayLog, type DayTrip,
} from '../composables/storyDay'
import { chargingEfficiencyPercent } from '../composables/useChargingEfficiency'
import { tripSpeedKeyAndArgs } from './recentActivity'

/**
 * Logbuch auf /logs: der Monat ist ein Kapitel, darin ein Blatt je Tag mit Einträgen, neueste zuerst.
 * Tage ohne Eintrag fassen sich zu einer Zeile zusammen, damit ein abgestelltes Auto keine Liste leerer
 * Tage erzeugt. Die Standzeit vor einem Eintrag steht im Blatt des neueren Tages, nur mit Standverlust;
 * liegt eine solche Zeile dazwischen, steht sie dort.
 */
export interface Standing { minutes: number; kwh: number | null; eur: number | null }

export type LogbookItem =
  | { kind: 'day'; key: string; events: DayEvent[]; standing: Record<string, Standing>; costPer100km: number | null }
  | { kind: 'idle'; from: string; to: string; standing: Standing | null }

export interface LogbookTotals { trips: number; km: number; charges: number; eur: number; costPer100km: number | null }
export interface LogbookMonth { key: string; items: LogbookItem[]; totals: LogbookTotals }

export interface LogbookOptions {
  /** Standverlust der Standzeit vor einem Eintrag, aus dem Feed (annotatePhantomDrains, phantomEurFor). */
  drains?: Map<string, { kwh: number; eur: number | null }>
  /** € pro 100 km einer Menge Fahrten (tripGroupCostPer100km), hier keine eigene Formel. */
  costPer100km?: (trips: DayTrip[]) => number | null
}

const startMs = (e: DayEvent) => new Date(e.kind === 'trip' ? e.trip.tripStartedAt : e.log.loggedAt).getTime()
const endMs = (e: DayEvent) => e.kind === 'trip'
  ? new Date(e.trip.tripEndedAt).getTime()
  : startMs(e) + Math.max(1, e.log.chargeDurationMinutes ?? 1) * 60_000
const monthOf = (key: string) => key.slice(0, 7)

/** Standzeit vor jedem Eintrag, über Tagesgrenzen hinweg; nur wo ein Standverlust anfiel (und sichtbar ist). */
function standingBefore(days: { events: DayEvent[] }[], drains?: LogbookOptions['drains']): Map<string, Standing> {
  const before = new Map<string, Standing>()
  let prevEnd: number | null = null
  for (const e of days.flatMap(d => d.events)) {
    if (prevEnd != null) {
      const drain = drains?.get(e.id)
      const minutes = Math.round((startMs(e) - prevEnd) / 60_000)
      // Überlappen sich zwei Einträge (Ladung läuft noch, Fahrt beginnt), gibt es keine Standzeit
      if (drain && minutes > 0) before.set(e.id, { minutes, kwh: drain.kwh, eur: drain.eur })
    }
    prevEnd = Math.max(prevEnd ?? 0, endMs(e))
  }
  return before
}

/** Tage ohne Eintrag (neueste zuerst) als Zeilen, am Monatswechsel geteilt. */
function idleItems(run: string[], standing: Standing | null): LogbookItem[] {
  const items: LogbookItem[] = []
  for (const key of run) {
    const last = items[items.length - 1]
    if (last?.kind === 'idle' && monthOf(last.from) === monthOf(key)) last.from = key
    else items.push({ kind: 'idle', from: key, to: key, standing: items.length ? null : standing })
  }
  return items
}

export function monthsOf(trips: DayTrip[], logs: DayLog[], opts: LogbookOptions = {}): LogbookMonth[] {
  // Einmal je Tag gruppieren: eventsOfDay je Tag über alle Einträge wäre Tage mal Einträge
  const byDay = new Map<string, { trips: DayTrip[]; logs: DayLog[] }>()
  const bucket = (key: string) => byDay.get(key) ?? byDay.set(key, { trips: [], logs: [] }).get(key)!
  for (const t of trips) bucket(dayKeyOf(t.tripStartedAt)).trips.push(t)
  for (const l of logs) bucket(dayKeyOf(l.loggedAt)).logs.push(l)
  const keys = [...byDay.keys()].sort()
  if (!keys.length) return []
  const days = keys.map(key => ({ key, events: eventsOfDay(key, byDay.get(key)!.trips, byDay.get(key)!.logs) }))
  const before = standingBefore(days, opts.drains)
  const byKey = new Map(days.map(d => [d.key, d]))

  const items: LogbookItem[] = []
  let newer: (LogbookItem & { kind: 'day' }) | null = null
  let run: string[] = []
  // Vom jüngsten Eintrag abwärts: über ihm steht keine Zeile ohne Eintrag
  for (let key = keys[keys.length - 1]; key >= keys[0]; key = shiftDay(key, -1)) {
    const day = byKey.get(key)
    if (!day) { run.push(key); continue }
    if (run.length) {
      // Die Standzeit über die leeren Tage gehört in deren Zeile, nicht ins Blatt darüber
      const firstId = newer?.events[0]?.id
      const standing = firstId ? before.get(firstId) ?? null : null
      if (newer && firstId) delete newer.standing[firstId]
      items.push(...idleItems(run, standing))
      run = []
    }
    newer = {
      kind: 'day', key, events: day.events,
      standing: Object.fromEntries(day.events.flatMap(e => before.has(e.id) ? [[e.id, before.get(e.id)!]] : [])),
      costPer100km: opts.costPer100km?.(day.events.flatMap(e => e.kind === 'trip' ? [e.trip] : [])) ?? null,
    }
    items.push(newer)
  }

  const months: LogbookMonth[] = []
  for (const item of items) {
    const key = monthOf(item.kind === 'day' ? item.key : item.from)
    if (months[months.length - 1]?.key !== key) months.push({ key, items: [], totals: { trips: 0, km: 0, charges: 0, eur: 0, costPer100km: null } })
    months[months.length - 1].items.push(item)
  }
  for (const m of months) {
    const events = m.items.flatMap(i => i.kind === 'day' ? i.events : [])
    const sum = daySummary(events)
    const monthTrips = events.flatMap(e => e.kind === 'trip' ? [e.trip] : [])
    m.totals = { trips: sum.trips, km: sum.km, charges: sum.charges, eur: sum.eur, costPer100km: opts.costPer100km?.(monthTrips) ?? null }
  }
  return months
}

/** Textbaustein für i18n: Schlüssel plus Parameter. */
export interface TextKey { key: string; params?: Record<string, number> }

/**
 * Weitere Angaben einer Fahrt wie im aufgeklappten Bereich der alten Liste: Tempo (Ø und max),
 * Streckentyp, Besonderheiten. Im Logbuch ab 768 px in der Zeile, auf dem Handy im Detail.
 */
export function tripDetailKeys(trip: {
  avgSpeedKmh?: number | null; maxSpeedKmh?: number | null; routeType?: string | null; loadExtras?: string[] | null
}): TextKey[] {
  const keys: TextKey[] = []
  const speed = tripSpeedKeyAndArgs(trip.avgSpeedKmh, trip.maxSpeedKmh)
  if (speed) keys.push({ key: speed.key, params: speed.args })
  if (trip.routeType) keys.push({ key: `dashboard.trip_route_${trip.routeType.toLowerCase()}` })
  for (const x of trip.loadExtras ?? []) keys.push({ key: `storyday.form_extra_${x.toLowerCase()}` })
  return keys
}

/** Weitere Angaben einer Ladung: höchste Ladeleistung und Effizienz. Ab 768 px in der Zeile, auf dem Handy im Detail. */
export function chargeDetailKeys(log: { maxChargingPowerKw?: number | null; kwhCharged: number | null; kwhAtVehicle: number | null }): TextKey[] {
  const keys: TextKey[] = []
  if (log.maxChargingPowerKw != null) keys.push({ key: 'logbook.max_power', params: { kw: Math.round(Number(log.maxChargingPowerKw)) } })
  const eff = chargingEfficiencyPercent(log.kwhCharged, log.kwhAtVehicle)
  if (eff != null) keys.push({ key: 'logbook.efficiency', params: { pct: Math.round(eff) } })
  return keys
}

/** Ladung im Logbuch ohne Fahrten: dazu, was das Backend je Ladung rechnet (EvLogService). */
export interface ChargeLog extends DayLog {
  odometerKm?: number | null
  distanceSinceLastChargeKm?: number | null
  consumptionKwhPer100km?: number | null
}
export type ChargeEntry =
  | { kind: 'charge'; key: string; log: ChargeLog }
  /** Strecke zwischen zwei Ladungen, gehört zur neueren. `fix`: Ladung, der der km-Stand fehlt. */
  | { kind: 'stretch'; key: string; days: number; km: number | null; kwhPer100km: number | null; eurPer100km: number | null; fix: string | null }
export interface ChargeMonth {
  key: string
  entries: ChargeEntry[]
  totals: { charges: number; kwh: number; eur: number }
  /** Laderhythmus: Median der Abstände in ganzen Tagen, erst ab zwei Abständen */
  rhythmDays: number | null
}

const DAY_MS = 86_400_000

function median(values: number[]): number | null {
  if (values.length < 2) return null
  const v = [...values].sort((a, b) => a - b)
  const mid = v.length / 2
  // "etwa alle 0 Tage" gibt es nicht: mehrmals am Tag heißt "etwa jeden Tag"
  return Math.max(1, Math.round(v.length % 2 ? v[Math.floor(mid)] : (v[mid - 1] + v[mid]) / 2))
}

/**
 * Logbuch für Autos ohne Fahrten: eine Ladung je Zeile, neueste zuerst, dazwischen die Strecke aus den
 * km-Ständen. Strecke und Verbrauch kommen aus dem Backend; € pro 100 km ist der Betrag der neueren Ladung
 * geteilt durch die Strecke, keine Verbrauchsformel. Nachladen am selben km-Stand bekommt keine Strecke,
 * unter der ältesten geladenen Ladung steht keine.
 */
export function chargeMonthsOf(logs: ChargeLog[]): ChargeMonth[] {
  const sorted = [...logs].sort((a, b) => Date.parse(b.loggedAt) - Date.parse(a.loggedAt))
  const months: ChargeMonth[] = []
  const gaps = new Map<string, number[]>()
  sorted.forEach((log, i) => {
    const key = monthOf(dayKeyOf(log.loggedAt))
    let month = months[months.length - 1]
    if (month?.key !== key) {
      month = { key, entries: [], totals: { charges: 0, kwh: 0, eur: 0 }, rhythmDays: null }
      months.push(month)
    }
    month.entries.push({ kind: 'charge', key: log.id, log })
    month.totals.charges++
    month.totals.kwh += chargeKwh(log) ?? 0
    month.totals.eur += log.costEur ?? 0

    const older = sorted[i + 1]
    const km = log.distanceSinceLastChargeKm ?? null
    if (!older || km === 0) return
    const days = Math.round((Date.parse(log.loggedAt) - Date.parse(older.loggedAt)) / DAY_MS)
    if (!gaps.has(key)) gaps.set(key, [])
    gaps.get(key)!.push(days)
    // Das Backend misst die Strecke bis zur letzten Ladung MIT km-Stand. Fehlt er der älteren hier, reichte die
    // Strecke weiter zurück als dieser Abstand: dann keine Zahlen, sondern den km-Stand dort nachtragen.
    const direct = log.odometerKm != null && older.odometerKm != null
    const shownKm = direct ? km : null
    const fix = shownKm != null ? null : log.odometerKm == null ? log.id : older.odometerKm == null ? older.id : null
    if (shownKm == null && !fix) return
    month.entries.push({
      kind: 'stretch', key: `s-${log.id}`, days, km: shownKm,
      kwhPer100km: shownKm != null ? log.consumptionKwhPer100km ?? null : null,
      eurPer100km: shownKm && log.costEur != null ? log.costEur / shownKm * 100 : null,
      fix,
    })
  })
  for (const month of months) month.rhythmDays = median(gaps.get(month.key) ?? [])
  return months
}

/** "Oktober 2026" aus dem Monatsschlüssel "2026-10" */
export function monthName(key: string, locale: string): string {
  const [y, m] = key.split('-').map(Number)
  return new Date(y, m - 1, 1).toLocaleDateString(locale, { month: 'long', year: 'numeric' })
}
