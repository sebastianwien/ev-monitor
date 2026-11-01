/**
 * Aggregiert die Fahrten eines Zeitraums zu einer Monats-Zusammenfassung fuer das Dashboard,
 * wenn im Zeitraum zwar Fahrten, aber keine Ladung vorliegen (der bisherige "keine Daten"-Fall).
 *
 * Bewusst frei von Vue-Imports und von der Verbrauchsformel des EvLogService: hier werden nur
 * die auf den Trips gespeicherten Telemetriewerte summiert (Antriebsverbrauch), NICHT der
 * nachgeladene Verbrauch. Die beiden Groessen sind nicht dasselbe - siehe TripActivitySummaryCard.
 */
import { tripGroupConsumedKwh, type TripForCalc } from './tripCalculations'
import { drivenByDay, type OdometerGap } from '../composables/storyDay'
import { odometerMoved } from './phantomDrain'

export interface TripForSummary extends TripForCalc {
  energyRemainingStartKwh?: number | string | null
  energyRemainingEndKwh?: number | string | null
  odometerStartKm?: number | string | null
  odometerEndKm?: number | string | null
  outsideTempCelsius?: number | null
}

export interface DayKm {
  dateKey: string
  km: number
  trips: number
}

export interface TripMonthSummary {
  tripCount: number
  /** Fahrten plus Strecke ohne Aufzeichnung: was der Kilometerstand belegt */
  totalDistanceKm: number
  /** Nur Fahrten: Basis für Verbrauch je 100 km und Schnitt je Fahrt */
  tripDistanceKm: number
  activeDays: number
  /** Antriebsverbrauch (kWh), aus den Trips summiert. Null wenn kein Trip Energiedaten hat. */
  drivetrainKwh: number | null
  /** Antriebsverbrauch pro 100 km. Null bei fehlender Strecke oder Energie. */
  consumptionKwhPer100km: number | null
  /** Standby-/Phantomverlust zwischen den Fahrten (kWh). Gegatetes Analytics-Artefakt. Null ohne Energiedaten. */
  standbyKwh: number | null
  tempMin: number | null
  tempMax: number | null
  /** Strecke je Kalendertag, aufsteigend - Grundlage fuer den Balkenstreifen. */
  perDay: DayKm[]
}

const toNum = (v: number | string | null | undefined): number | null => {
  if (v == null) return null
  const n = typeof v === 'string' ? parseFloat(v) : v
  return Number.isFinite(n) ? n : null
}

const toMillis = (d: string | Date | null | undefined): number | null => {
  if (d == null) return null
  const ms = d instanceof Date ? d.getTime() : new Date(d).getTime()
  return Number.isFinite(ms) ? ms : null
}

/** Tag aus dem Datum im ISO-Text, wie die Zeitstempel vom Backend kommen */
const isoDay = (at: string | Date): string => (typeof at === 'string' ? at : at.toISOString()).slice(0, 10)

const round = (v: number, digits: number): number => {
  const f = 10 ** digits
  return Math.round(v * f) / f
}

/**
 * Standby = Summe der Energieabfaelle waehrend das Auto zwischen zwei Fahrten steht.
 * Verglichen wird der Energiezaehler am Ende einer Fahrt mit dem Beginn der naechsten. Stieg der km-Stand
 * dazwischen, ist das Auto ohne Aufzeichnung gefahren: dieser Abstand zaehlt nicht.
 * Voraussetzung des Aufrufers: im Zeitraum liegt keine Ladung (sonst springt der Zaehler hoch).
 * Trips muessen aufsteigend nach Startzeit sortiert sein.
 */
function standbyBetweenTrips(sortedAsc: TripForSummary[]): number | null {
  let total = 0
  let any = false
  for (let i = 0; i < sortedAsc.length - 1; i++) {
    const end = toNum(sortedAsc[i].energyRemainingEndKwh)
    const nextStart = toNum(sortedAsc[i + 1].energyRemainingStartKwh)
    if (end == null || nextStart == null) continue
    if (odometerMoved(toNum(sortedAsc[i].odometerEndKm), toNum(sortedAsc[i + 1].odometerStartKm))) continue
    any = true
    const gap = end - nextStart
    if (gap > 0) total += gap
  }
  return any ? round(total, 2) : null
}

/**
 * `gaps`: Strecke ohne Aufzeichnung im selben Fenster. Sie zählt in Strecke und Tages-km (am Tag ihres Endes),
 * nicht in Fahrten, Verbrauch und Energie: deren Zeit und Energie sind unbekannt.
 */
export function summarizeTripMonth(
  trips: TripForSummary[],
  effectiveBatteryCapacityKwh: number | null | undefined,
  gaps: OdometerGap[] = [],
): TripMonthSummary | null {
  if (!Array.isArray(trips) || trips.length === 0) return null

  const sorted = [...trips].sort((a, b) => (toMillis(a.tripStartedAt) ?? 0) - (toMillis(b.tripStartedAt) ?? 0))

  const tripDistanceKm = round(sorted.reduce((s, t) => s + (t.distanceKm ?? 0), 0), 1)
  const totalDistanceKm = round(tripDistanceKm + gaps.reduce((s, g) => s + g.distanceKm, 0), 1)
  const drivetrainRaw = tripGroupConsumedKwh(sorted, effectiveBatteryCapacityKwh)
  const drivetrainKwh = drivetrainRaw != null ? round(drivetrainRaw, 2) : null
  const consumptionKwhPer100km = (drivetrainRaw != null && tripDistanceKm > 0)
    ? round((drivetrainRaw / tripDistanceKm) * 100, 1)
    : null

  const standbyKwh = standbyBetweenTrips(sorted)

  let tempMin: number | null = null
  let tempMax: number | null = null
  for (const t of sorted) {
    if (t.outsideTempCelsius == null) continue
    tempMin = tempMin == null ? t.outsideTempCelsius : Math.min(tempMin, t.outsideTempCelsius)
    tempMax = tempMax == null ? t.outsideTempCelsius : Math.max(tempMax, t.outsideTempCelsius)
  }

  // Tag wie bisher aus dem Datum im ISO-Text, die Lücke am Tag ihres Endes
  const perDay: DayKm[] = [...drivenByDay(sorted, gaps, isoDay)]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([dateKey, d]) => ({ dateKey, km: round(d.km, 1), trips: d.trips }))

  return {
    tripCount: sorted.length,
    totalDistanceKm,
    tripDistanceKm,
    activeDays: perDay.length,
    drivetrainKwh,
    consumptionKwhPer100km,
    standbyKwh,
    tempMin,
    tempMax,
    perDay,
  }
}

export interface TripWindow {
  startMs: number
  endMs: number
}

/**
 * Loest die symbolische Zeitraum-Auswahl des Dashboards in ein konkretes Fenster auf, damit die
 * Trip-Zusammenfassung dasselbe Fenster abdeckt wie die Ladestatistik. Monatsgrenzen werden in UTC
 * gebildet (deterministisch, unabhaengig von der Browser-Zeitzone). ALL_TIME ist unbegrenzt (null).
 */
export function resolveTripWindow(
  timeRange: string,
  customStart: string | null,
  customEnd: string | null,
  now: Date,
): TripWindow | null {
  const y = now.getUTCFullYear()
  const m = now.getUTCMonth()
  const monthStart = (offset: number) => Date.UTC(y, m - offset, 1)

  switch (timeRange) {
    case 'THIS_MONTH':
      return { startMs: monthStart(0), endMs: now.getTime() }
    case 'LAST_MONTH':
      return { startMs: monthStart(1), endMs: monthStart(0) - 1 }
    case 'LAST_3_MONTHS':
      return { startMs: monthStart(2), endMs: now.getTime() }
    case 'LAST_6_MONTHS':
      return { startMs: monthStart(5), endMs: now.getTime() }
    case 'LAST_12_MONTHS':
      return { startMs: monthStart(11), endMs: now.getTime() }
    case 'THIS_YEAR':
      return { startMs: Date.UTC(y, 0, 1), endMs: now.getTime() }
    case 'CUSTOM': {
      const s = customStart ? Date.parse(customStart) : NaN
      const e = customEnd ? Date.parse(customEnd) : NaN
      if (!Number.isFinite(s) || !Number.isFinite(e)) return null
      // customEnd is a date -> include the whole day
      return { startMs: s, endMs: e + 86_400_000 - 1 }
    }
    default:
      return null // ALL_TIME and unknown -> unbounded
  }
}

/** Strecke ohne Aufzeichnung im Fenster, nach ihrem Ende (dem Start der Fahrt danach). */
export function gapsInWindow(gaps: OdometerGap[], window: TripWindow | null): OdometerGap[] {
  if (!window) return gaps
  return gaps.filter(g => {
    const ms = toMillis(g.to)
    return ms != null && ms >= window.startMs && ms <= window.endMs
  })
}

/** Filtert Trips auf ein Fenster (nach Startzeit). Ein null-Fenster laesst alle Trips durch. */
export function tripsInWindow(trips: TripForSummary[], window: TripWindow | null): TripForSummary[] {
  if (!window) return trips
  return trips.filter(t => {
    const ms = toMillis(t.tripStartedAt)
    return ms != null && ms >= window.startMs && ms <= window.endMs
  })
}
