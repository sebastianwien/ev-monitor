import { tripTimestamp } from '../utils/recentActivity'
import { tripConsumption, type TripForCalc } from '../utils/tripCalculations'
import { calcCostPer100km } from './useDashboardStats'
import { summarizeTripMonth, type TripForSummary } from '../utils/tripMonthSummary'
import type { PeerModelComparisonResponse } from '../api/peerModelComparisonService'

/**
 * Erkenntnisse fuer den Story-Feed: Temperatur-Effekt aus Fahrten, Vergleich mit allen
 * Fahrern des eigenen Modells, Datenqualitaet des Monats, bester Community-Rang.
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

/** Preis, Verbrauch und was beides zusammen je 100 km kostet. */
export interface PeerValues {
  costPerKwh: number
  consumption: number
  costPer100km: number
}

export interface ModelVariant {
  name: string
  costPer100km: number
  cars: number
  isUser: boolean
}

/** Du gegen alle Fahrer deines Modells, über alle Varianten (Lifetime). */
export interface ModelPeers {
  /** Autos aller Varianten, ohne deine. */
  cars: number
  theirs: PeerValues
  mine: PeerValues
  /** Ihre Euro je 100 km gegen deine, negativ heißt: sie zahlen weniger. */
  deltaPercent: number
  /** Was den Unterschied mehr erklärt: Ladepreis oder Verbrauch. */
  reason: 'price' | 'consumption'
  /** Je Variante mit vollständigen Werten, aufsteigend nach Euro je 100 km. */
  variants: ModelVariant[]
}

function peerValues(costPerKwh: number | null | undefined, consumption: number | null | undefined): PeerValues | null {
  const cost = calcCostPer100km(costPerKwh ?? null, consumption ?? null)
  return cost == null || cost <= 0 ? null : { costPerKwh: costPerKwh!, consumption: consumption!, costPer100km: cost }
}

/**
 * Vergleich mit allen Fahrern des Modells statt mit einer einzelnen Variante. Der Grund ist der
 * größere der beiden relativen Unterschiede, Preis oder Verbrauch. Null ohne eigenen Wert oder
 * ohne Werte der anderen; die Mindestzahl an Autos prüft der Aufrufer.
 */
export function modelPeers(
  res: Pick<PeerModelComparisonResponse, 'modelComparisons' | 'allVariants'> | null,
  own: { costPerKwh: number | null; consumption: number | null } | null,
): ModelPeers | null {
  const all = res?.allVariants
  const theirs = peerValues(all?.avgCostPerKwh, all?.avgConsumptionKwhPer100km)
  const mine = own ? peerValues(own.costPerKwh, own.consumption) : null
  if (!all || !theirs || !mine) return null
  const priceShare = Math.abs(theirs.costPerKwh / mine.costPerKwh - 1)
  const consumptionShare = Math.abs(theirs.consumption / mine.consumption - 1)
  const variants: ModelVariant[] = []
  for (const it of res!.modelComparisons) {
    const v = peerValues(it.peerMetrics.avgCostPerKwh, it.peerMetrics.avgConsumptionKwhPer100km)
    if (v) variants.push({ name: modelNameWithoutCapacity(it.displayName), costPer100km: v.costPer100km, cars: it.peerMetrics.uniqueCars, isUser: it.isUserVehicleSpec })
  }
  return {
    cars: all.uniqueCars,
    theirs,
    mine,
    deltaPercent: ((theirs.costPer100km - mine.costPer100km) / mine.costPer100km) * 100,
    reason: priceShare >= consumptionShare ? 'price' : 'consumption',
    variants: variants.sort((a, b) => a.costPer100km - b.costPer100km),
  }
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

export type QualitySeverity = 'ok' | 'warn' | 'bad'

export interface DataQuality {
  estimated: number
  total: number
  /** 0 bis 1. */
  estimatedShare: number
  priceless: number
  /** Davon noch nachtragbar: ohne "Weiß ich nicht mehr". */
  pricelessOpen: number
  /** Genau eine nachtragbare Ladung ohne Preis: ihre id, damit die Zeile sie direkt öffnet */
  pricelessOpenLogId?: string | null
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

/** Logs, deren loggedAt (UTC) im Monat liegt. */
export function pricelessInMonth<T extends { loggedAt: string }>(logs: T[], year: number, month: number): T[] {
  return logs.filter(l => {
    const d = new Date(l.loggedAt)
    return d.getUTCFullYear() === year && d.getUTCMonth() + 1 === month
  })
}

/** Zaehlt Logs, deren loggedAt (UTC) im Monat liegt. */
export function countPricelessInMonth(logs: Array<{ loggedAt: string }>, year: number, month: number): number {
  return pricelessInMonth(logs, year, month).length
}

export interface RankEntry {
  rank: number
  value: number
  unit: string
}

/** Eine Zeile von GET /public/leaderboard/standings/me: Rang und Wert sind null ohne Platzierung. */
export interface LeaderboardStanding {
  category: string
  unit: string
  rank: number | null
  value: number | null
}

/**
 * Die eigenen Plätze aus standings/me in die Form der Rang-Auswertung. Die Liste je Kategorie
 * nennt den eigenen Platz nur ab Platz 11 (davor steht er in den Top 10), standings/me immer.
 */
export function ranksFromStandings(standings: LeaderboardStanding[]): Array<{ category: string; ownEntry: RankEntry | null }> {
  return standings.map(s => ({
    category: s.category,
    ownEntry: s.rank != null && s.value != null ? { rank: s.rank, value: s.value, unit: s.unit ?? '' } : null,
  }))
}


export interface LatestCharge {
  id: string
  at: string
  kwh: number | null
  costEur: number | null
  cpoName?: string | null
  chargingType?: string | null
  /** Ladestand vor und nach der Ladung, nur wenn beide stimmig sind. */
  socFrom: number | null
  socTo: number | null
  /** Reichweite nach der Ladung, auf 10 km gerundet: Ladestand mal effektive Kapazität durch Verbrauch der letzten 4 Wochen. */
  rangeKm: number | null
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
  socBeforeChargePercent?: number | null
  socAfterChargePercent?: number | null
}

/** Verbrauch der letzten 4 Wochen und effektive Kapazität (SoH-bereinigt) für die Reichweite der letzten Ladung. */
export interface LatestRangeBasis {
  consumptionKwh100: number | null
  effectiveCapacityKwh: number | null
}

export interface LatestTripSource {
  id: string
  routeType?: string | null
  tripStartedAt?: string | null
  tripEndedAt?: string | null
  distanceKm?: number | null
}

/** Unter dieser Strecke ist eine Fahrt Umparken: die Karte "Letzte Fahrt" zeigt dann die Fahrt davor. */
const LATEST_TRIP_MIN_KM = 0.5

/**
 * Fahrt für die Karte "Letzte Fahrt": die neueste ab {@link LATEST_TRIP_MIN_KM} aus den Fahrten der letzten
 * Tage, sonst die neueste überhaupt, damit die Karte nicht leer bleibt.
 */
export function pickLatestTrip(latest: LatestTripSource | null | undefined, recent: readonly LatestTripSource[]): LatestTripSource | null {
  const end = (t: LatestTripSource) => Date.parse(tripTimestamp(t) ?? '') || 0
  const real = recent.filter(t => (t.distanceKm ?? 0) >= LATEST_TRIP_MIN_KM).sort((a, b) => end(b) - end(a))[0]
  if (!latest) return real ?? null
  return (latest.distanceKm ?? 0) >= LATEST_TRIP_MIN_KM || !real ? latest : real
}

/** Fahrzeug-kWh vor Saeulen-kWh (wie die Ladeliste), Fahrt nach ihrem Ende datiert. Null ohne beides. */
export function buildLatest(log: LatestLogSource | null | undefined, trip: LatestTripSource | null | undefined,
  basis: LatestRangeBasis = { consumptionKwh100: null, effectiveCapacityKwh: null }): StoryLatest | null {
  const from = log?.socBeforeChargePercent ?? null
  const to = log?.socAfterChargePercent ?? null
  const socOk = from != null && to != null && to > from
  const c = basis.consumptionKwh100
  const cap = basis.effectiveCapacityKwh
  const rangeKm = socOk && c != null && c > 0 && cap != null && cap > 0 ? Math.round((to / 100) * cap / c * 100 / 10) * 10 : null
  const charge: LatestCharge | null = log
    ? {
      id: log.id, at: log.loggedAt, kwh: log.kwhAtVehicle ?? log.kwhCharged ?? null, costEur: log.costEur ?? null,
      ...(log.cpoName ? { cpoName: log.cpoName } : {}),
      ...(log.chargingType ? { chargingType: log.chargingType } : {}),
      socFrom: socOk ? from : null, socTo: socOk ? to : null, rangeKm,
    }
    : null
  const at = tripTimestamp(trip)
  const latestTrip: LatestTrip | null = trip && at
    ? { id: trip.id, at, km: trip.distanceKm ?? null, ...(trip.routeType ? { routeType: trip.routeType } : {}) }
    : null
  if (!charge && !latestTrip) return null
  return { charge, trip: latestTrip }
}

export type RouteKey = 'CITY' | 'COMBINED' | 'HIGHWAY'
const ROUTE_KEYS: RouteKey[] = ['CITY', 'COMBINED', 'HIGHWAY']
/** Ab diesem Anteil an den km gilt ein Streckentyp als Schwerpunkt ("meist Stadt"). */
const MAIN_ROUTE_SHARE = 50
export type LengthKey = 'SHORT' | 'MEDIUM' | 'LONG'
/** Grenzen der Fahrtlängen in km: kurz bis unter 5, mittel bis unter 30, lang ab 30. Kurze Fahrten treiben den Verbrauch. */
const SHORT_KM = 5
const LONG_KM = 30

/** Fahrten eines Monats für Kachel und Sheet "Wie fahre ich?". */
export interface TripMonth {
  trips: number
  km: number
  /** Durchschnittliche Länge einer Fahrt in km. */
  avgKm: number
  /** Anteil der km je Fahrtlänge in ganzen Prozent, ohne leere Klassen. */
  lengthShares: { key: LengthKey; pct: number }[]
  /** Antriebsverbrauch laut Fahrzeug, kWh/100 km. Null ohne Energiedaten der Fahrten. */
  driveKwhPer100km: number | null
  /** Antrieb laut Fahrzeug in kWh über alle Fahrten. Null ohne Energiedaten der Fahrten. */
  driveKwh: number | null
  /** Anteile an den km je Streckentyp in ganzen Prozent; leer ohne Streckentypen. */
  routeShares: { key: RouteKey; pct: number }[]
  mainRoute: RouteKey | null
  longestKm: number
  tempMin: number | null
  tempMax: number | null
  /** Verlust zwischen den Fahrten (kWh). Null, wenn nicht freigeschaltet oder ohne Energiezähler. */
  standbyKwh: number | null
  standbyLocked: boolean
  /** Größte Verluste im Stand (höchstens sechs, größter zuerst); leer, wenn nicht freigeschaltet. */
  standbyGaps: StandbyGap[]
  /** Alle Standverluste des Monats in Euro (siehe StandbyGap.eur); null ohne jeden Preis. */
  standbyEur: number | null
}

/** Ein Stand zwischen zwei Fahrten, in dem der Akku Energie verloren hat. */
export interface StandbyGap {
  kwh: number
  from: string
  to: string
  /** Wert in Euro zum Preis der letzten Ladung mit Preis davor, sonst zum Monatsschnitt; null ohne Preis. */
  eur: number | null
}

/** Was der Preis eines Standverlusts braucht: Ladungen mit Zeit und Kosten, dazu der Monatsschnitt. */
export interface StandbyPricing {
  charges: { loggedAt: string; costEur: number | null; kwhCharged: number | null; kwhAtVehicle?: number | null }[]
  avgCostPerKwh: number | null
}

const STANDBY_GAPS_MAX = 6

/** Größte Standzeiten für Kachel und Sheet, Euro über alle Standzeiten des Monats. */
function standbyOf(trips: TripForMonth[], visible: boolean, pricing: StandbyPricing | undefined): Pick<TripMonth, 'standbyGaps' | 'standbyEur'> {
  if (!visible) return { standbyGaps: [], standbyEur: null }
  const all = standbyGapsOf(trips, pricing)
  const priced = all.filter(g => g.eur != null)
  return {
    standbyGaps: all.slice(0, STANDBY_GAPS_MAX),
    standbyEur: priced.length ? Math.round(priced.reduce((a, g) => a + g.eur!, 0) * 100) / 100 : null,
  }
}
const iso = (d: string | Date | null) => (d instanceof Date ? d.toISOString() : d ?? '')
const kwhOf = (v: number | string | null | undefined) => (v == null ? null : typeof v === 'string' ? parseFloat(v) : v)

/** Abfall des Energiezählers zwischen Ende einer Fahrt und Beginn der nächsten; ein Anstieg heißt geladen und zählt nicht. */
function standbyGapsOf(trips: TripForMonth[], pricing: StandbyPricing | undefined): StandbyGap[] {
  const sorted = [...trips].sort((a, b) => new Date(a.tripStartedAt ?? 0).getTime() - new Date(b.tripStartedAt ?? 0).getTime())
  const gaps: StandbyGap[] = []
  for (let i = 0; i < sorted.length - 1; i++) {
    const end = kwhOf(sorted[i].energyRemainingEndKwh), next = kwhOf(sorted[i + 1].energyRemainingStartKwh)
    if (end == null || next == null || !(end - next > 0)) continue
    const kwh = Math.round((end - next) * 100) / 100
    const from = iso(sorted[i].tripEndedAt)
    const price = priceBefore(from, pricing)
    gaps.push({ kwh, from, to: iso(sorted[i + 1].tripStartedAt), eur: price == null ? null : Math.round(kwh * price * 100) / 100 })
  }
  return gaps.sort((a, b) => b.kwh - a.kwh)
}

/** €/kWh der letzten Ladung mit Preis vor dem Zeitpunkt (dort wurde der verlorene Strom gekauft), sonst der Monatsschnitt. */
function priceBefore(at: string, pricing: StandbyPricing | undefined): number | null {
  if (!pricing) return null
  const t = new Date(at).getTime()
  let best: { at: number; price: number } | null = null
  for (const c of pricing.charges) {
    const kwh = c.kwhCharged ?? c.kwhAtVehicle ?? null
    const ct = new Date(c.loggedAt).getTime()
    if (c.costEur == null || kwh == null || kwh <= 0 || ct > t) continue
    if (!best || ct > best.at) best = { at: ct, price: c.costEur / kwh }
  }
  if (best) return best.price
  return pricing.avgCostPerKwh != null && pricing.avgCostPerKwh > 0 ? pricing.avgCostPerKwh : null
}

export type TripForMonth = TripForSummary & { routeType?: string | null }

/**
 * Baut die Monatszahlen der Fahrten aus der vorhandenen Auswertung (summarizeTripMonth) plus den
 * Streckentypen. Der Standverlust ist ein Analytics-Wert und nur mit Freischaltung sichtbar.
 */
export function buildTripMonth(trips: TripForMonth[], effectiveCapacityKwh: number | null, standbyVisible: boolean, pricing?: StandbyPricing): TripMonth | null {
  const sum = summarizeTripMonth(trips, effectiveCapacityKwh)
  if (!sum || sum.totalDistanceKm <= 0) return null
  const kmByRoute = new Map<RouteKey, number>()
  for (const t of trips) {
    const key = t.routeType as RouteKey
    if (!ROUTE_KEYS.includes(key)) continue
    kmByRoute.set(key, (kmByRoute.get(key) ?? 0) + (t.distanceKm ?? 0))
  }
  const typedKm = [...kmByRoute.values()].reduce((a, b) => a + b, 0)
  const routeShares = typedKm > 0
    ? ROUTE_KEYS.filter(k => (kmByRoute.get(k) ?? 0) > 0).map(k => ({ key: k, pct: Math.round(((kmByRoute.get(k) ?? 0) / typedKm) * 100) }))
    : []
  const main = routeShares.find(r => r.pct >= MAIN_ROUTE_SHARE)
  const lengthOf = (km: number): LengthKey => km < SHORT_KM ? 'SHORT' : km < LONG_KM ? 'MEDIUM' : 'LONG'
  // Anteil der km, nicht der Fahrten: zwei Fahrten nach Bremen tragen mehr Strecke als fünfzig ums Eck
  const kmByLength = new Map<LengthKey, number>()
  for (const t of trips) kmByLength.set(lengthOf(t.distanceKm ?? 0), (kmByLength.get(lengthOf(t.distanceKm ?? 0)) ?? 0) + (t.distanceKm ?? 0))
  const lengthKm = [...kmByLength.values()].reduce((a, b) => a + b, 0)
  const lengthShares = (['SHORT', 'MEDIUM', 'LONG'] as LengthKey[])
    .filter(k => (kmByLength.get(k) ?? 0) > 0)
    .map(k => ({ key: k, pct: Math.round(((kmByLength.get(k) ?? 0) / lengthKm) * 100) }))
  return {
    trips: sum.tripCount,
    km: sum.totalDistanceKm,
    avgKm: sum.totalDistanceKm / sum.tripCount,
    lengthShares,
    driveKwhPer100km: sum.consumptionKwhPer100km,
    driveKwh: sum.drivetrainKwh,
    routeShares,
    mainRoute: main?.key ?? null,
    longestKm: Math.max(0, ...trips.map(t => t.distanceKm ?? 0)),
    tempMin: sum.tempMin,
    tempMax: sum.tempMax,
    standbyKwh: standbyVisible ? sum.standbyKwh : null,
    standbyLocked: !standbyVisible,
    ...standbyOf(trips, standbyVisible, pricing),
  }
}
