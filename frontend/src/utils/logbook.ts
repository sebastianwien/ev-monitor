import {
  eventsOfDay, dayKeyOf, shiftDay, daySummary, type DayEvent, type DayLog, type DayTrip,
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
