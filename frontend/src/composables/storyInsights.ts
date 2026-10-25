import { tripTimestamp } from '../utils/recentActivity'
import { tripConsumption, type TripForCalc } from '../utils/tripCalculations'
import { calcCostPer100km } from './useDashboardStats'
import type { PeerModelComparisonItem } from '../api/peerModelComparisonService'

/**
 * Erkenntnisse fuer den Story-Feed: Temperatur-Effekt aus Fahrten, Was-waere-wenn
 * ueber andere Modelle, Datenqualitaet des Monats, bester Community-Rang.
 * Reine Funktionen ohne Vue. Verbrauch je Fahrt kommt aus tripConsumption(),
 * es gibt hier keine eigene Verbrauchsformel.
 */

export interface TripForTemperature extends TripForCalc {
  outsideTempCelsius?: number | null
}

export interface TempPoint {
  temp: number
  kwh100: number
  km: number
}

export interface TempBin {
  tempFrom: number
  /** Streckengewichtet: Summe kWh durch Summe km, wie EvLogService den Monatswert bildet. */
  kwh100: number
  count: number
}

export interface TemperatureEffect {
  points: TempPoint[]
  bins: TempBin[]
  coldKwh100: number | null
  mildKwh100: number | null
  /** Mehrverbrauch kalt gegen mild in Prozent, null wenn eine Seite zu duenn ist. */
  deltaPercent: number | null
}

export const COLD_MAX_C = 5
export const MILD_MIN_C = 15
export const MIN_TRIPS_PER_SIDE = 3
const MIN_POINTS = 3
const BIN_SIZE_C = 5

/**
 * Streckengewichtetes Mittel wie im Backend (Summe kWh durch Summe km). Kurzstrecken bleiben als Punkt
 * sichtbar, ziehen das Mittel aber nur mit ihren wenigen Kilometern, nicht mit ihrem hohen Wert je 100 km.
 */
function weightedKwh100(points: TempPoint[]): number {
  let kwh = 0
  let km = 0
  for (const p of points) {
    kwh += (p.kwh100 * p.km) / 100
    km += p.km
  }
  return (kwh / km) * 100
}

export interface ScatterYScale {
  max: number
  ticks: number[]
  /** Fahrten über max: werden gezählt statt gezeichnet. */
  above: number
  aboveMax: number | null
}

/**
 * y-Achse fürs Temperatur-Diagramm: ab 0, glatte Striche. Eine einzelne Kurzfahrt mit 274 kWh/100 km
 * würde sonst alle anderen Punkte an den Boden drücken, daher endet die Achse knapp über dem
 * 90. Perzentil (oder dem Herstellerwert, falls höher); was darüber liegt, wird gezählt.
 */
export function scatterYScale(values: number[], reference: number | null): ScatterYScale {
  const sorted = [...values].sort((a, b) => a - b)
  const p90 = sorted.length ? sorted[Math.min(sorted.length - 1, Math.ceil(sorted.length * 0.9) - 1)] : 0
  const top = sorted.length ? sorted[sorted.length - 1] : 0
  const cap = Math.max(top <= p90 * 1.3 ? top : p90 * 1.3, reference ?? 0) * 1.1
  const step = cap > 60 ? 20 : cap > 30 ? 10 : 5
  const max = Math.max(step, Math.ceil(cap / step) * step)
  const ticks = Array.from({ length: max / step + 1 }, (_, i) => i * step)
  const over = sorted.filter(v => v > max)
  return { max, ticks, above: over.length, aboveMax: over.length ? over[over.length - 1] : null }
}

/** Null unter drei Fahrten mit Temperatur und Verbrauch. */
export function temperatureEffect(trips: TripForTemperature[], effectiveCapacityKwh: number | null): TemperatureEffect | null {
  const points: TempPoint[] = []
  for (const t of trips) {
    if (t.outsideTempCelsius == null || !t.distanceKm) continue
    const c = tripConsumption(t, effectiveCapacityKwh)
    if (!c || !Number.isFinite(c.kwhPer100km) || c.kwhPer100km <= 0) continue
    points.push({ temp: t.outsideTempCelsius, kwh100: c.kwhPer100km, km: t.distanceKm })
  }
  if (points.length < MIN_POINTS) return null
  points.sort((a, b) => a.temp - b.temp)

  const byBin = new Map<number, TempPoint[]>()
  for (const p of points) {
    const from = Math.floor(p.temp / BIN_SIZE_C) * BIN_SIZE_C
    byBin.set(from, [...(byBin.get(from) ?? []), p])
  }
  const bins = [...byBin.entries()]
    .sort((a, b) => a[0] - b[0])
    .map(([tempFrom, v]) => ({ tempFrom, kwh100: weightedKwh100(v), count: v.length }))

  const cold = points.filter(p => p.temp < COLD_MAX_C)
  const mild = points.filter(p => p.temp > MILD_MIN_C)
  const enough = cold.length >= MIN_TRIPS_PER_SIDE && mild.length >= MIN_TRIPS_PER_SIDE
  const coldKwh100 = enough ? weightedKwh100(cold) : null
  const mildKwh100 = enough ? weightedKwh100(mild) : null
  const deltaPercent = coldKwh100 != null && mildKwh100 != null && mildKwh100 > 0
    ? ((coldKwh100 - mildKwh100) / mildKwh100) * 100
    : null
  return { points, bins, coldKwh100, mildKwh100, deltaPercent }
}

export interface WhatIfModel {
  name: string
  costPer100km: number
  /** Gegen die Euro je 100 km des Nutzers, negativ ist guenstiger. */
  deltaPercent: number
  cars: number
  isUser: boolean
}

/**
 * Ob der Akkuwert eine Schätzung ist: der jüngste Eintrag im Verlauf (nach Datum, die API sagt
 * keine Reihenfolge zu) stammt nicht aus eigener Messung. Ohne Verlauf kommt der Wert aus der
 * eigenen Angabe am Auto.
 */
export function sohIsEstimated(history: { recordedAt: string; source: string }[]): boolean {
  let latest: { recordedAt: string; source: string } | null = null
  for (const e of history) if (!latest || Date.parse(e.recordedAt) > Date.parse(latest.recordedAt)) latest = e
  return latest != null && latest.source !== 'MANUAL'
}

/** "Model 3 Premium RWD Highland (82.00kWh)" wird "Model 3 Premium RWD Highland". */
export function modelNameWithoutCapacity(name: string): string {
  return name.replace(/\s*\([\d.,]+\s*kWh\)\s*$/i, '')
}

/** Leer ohne Nutzerwert. Modelle ohne Verbrauch oder Preis fallen weg. Aufsteigend nach Kosten. */
export function whatIfModels(items: PeerModelComparisonItem[], userCostPer100km: number | null): WhatIfModel[] {
  if (userCostPer100km == null || userCostPer100km <= 0) return []
  const out: WhatIfModel[] = []
  for (const it of items) {
    const cost = calcCostPer100km(it.peerMetrics.avgCostPerKwh, it.peerMetrics.avgConsumptionKwhPer100km)
    if (cost == null) continue
    out.push({
      name: modelNameWithoutCapacity(it.displayName),
      costPer100km: cost,
      deltaPercent: ((cost - userCostPer100km) / userCostPer100km) * 100,
      cars: it.peerMetrics.uniqueCars,
      isUser: it.isUserVehicleSpec,
    })
  }
  return out.sort((a, b) => a.costPer100km - b.costPer100km)
}

export type QualitySeverity = 'ok' | 'warn' | 'bad'

export interface DataQuality {
  estimated: number
  total: number
  /** 0 bis 1. */
  estimatedShare: number
  priceless: number
  /** Davon noch nachtragbar: ohne "Weiß ich nicht mehr". */
  pricelessOpen: number
  severity: QualitySeverity
}

const SHARE_WARN = 0.25
const SHARE_BAD = 0.5
const PRICELESS_BAD_SHARE = 0.25

export function dataQuality(stats: { totalCharges: number; estimatedConsumptionCount: number }, pricelessInMonth: number, openInMonth = pricelessInMonth): DataQuality {
  const total = stats.totalCharges
  const share = total > 0 ? stats.estimatedConsumptionCount / total : 0
  const pricelessShare = total > 0 ? pricelessInMonth / total : 0
  const severity: QualitySeverity = share >= SHARE_BAD || pricelessShare >= PRICELESS_BAD_SHARE ? 'bad'
    : share >= SHARE_WARN || pricelessInMonth > 0 ? 'warn'
    : 'ok'
  return { estimated: stats.estimatedConsumptionCount, total, estimatedShare: share, priceless: pricelessInMonth, pricelessOpen: openInMonth, severity }
}

/** Zaehlt Logs, deren loggedAt (UTC) im Monat liegt. */
export function countPricelessInMonth(logs: Array<{ loggedAt: string }>, year: number, month: number): number {
  return logs.filter(l => {
    const d = new Date(l.loggedAt)
    return d.getUTCFullYear() === year && d.getUTCMonth() + 1 === month
  }).length
}

export interface RankEntry {
  rank: number
  value: number
  unit: string
}

export interface RankResult extends RankEntry {
  category: string
}

/** Bester (kleinster) eigener Rang ueber alle Kategorien, bei Gleichstand der erste. */
export function bestRank(results: Array<{ category: string; ownEntry: RankEntry | null } | null>): RankResult | null {
  let best: RankResult | null = null
  for (const r of results) {
    if (!r?.ownEntry) continue
    if (!best || r.ownEntry.rank < best.rank) {
      best = { category: r.category, rank: r.ownEntry.rank, value: r.ownEntry.value, unit: r.ownEntry.unit }
    }
  }
  return best
}

export interface LatestCharge {
  id: string
  at: string
  kwh: number | null
  costEur: number | null
  cpoName?: string | null
  chargingType?: string | null
}

export interface LatestTrip {
  id: string
  at: string
  km: number | null
  /** CITY, COMBINED oder HIGHWAY. */
  routeType?: string | null
}

/** "Zuletzt"-Zeile im Hero: letzte Ladung und letzte Fahrt des Autos, unabhaengig vom gewaehlten Monat. */
export interface StoryLatest {
  charge: LatestCharge | null
  trip: LatestTrip | null
}

export interface LatestLogSource {
  id: string
  loggedAt: string
  kwhCharged?: number | null
  kwhAtVehicle?: number | null
  costEur?: number | null
  cpoName?: string | null
  chargingType?: string | null
}

export interface LatestTripSource {
  id: string
  routeType?: string | null
  tripStartedAt?: string | null
  tripEndedAt?: string | null
  distanceKm?: number | null
}

/** Fahrzeug-kWh vor Saeulen-kWh (wie die Ladeliste), Fahrt nach ihrem Ende datiert. Null ohne beides. */
export function buildLatest(log: LatestLogSource | null | undefined, trip: LatestTripSource | null | undefined): StoryLatest | null {
  const charge: LatestCharge | null = log
    ? {
      id: log.id, at: log.loggedAt, kwh: log.kwhAtVehicle ?? log.kwhCharged ?? null, costEur: log.costEur ?? null,
      ...(log.cpoName ? { cpoName: log.cpoName } : {}),
      ...(log.chargingType ? { chargingType: log.chargingType } : {}),
    }
    : null
  const at = tripTimestamp(trip)
  const latestTrip: LatestTrip | null = trip && at
    ? { id: trip.id, at, km: trip.distanceKm ?? null, ...(trip.routeType ? { routeType: trip.routeType } : {}) }
    : null
  if (!charge && !latestTrip) return null
  return { charge, trip: latestTrip }
}
