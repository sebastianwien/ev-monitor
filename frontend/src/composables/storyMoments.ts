import { tripTimestamp } from '../utils/recentActivity'
import { co2SavedKg, heroLine, petrolCostEur } from './storyModel'
import { COLD_MAX_C, type RankEntry } from './storyInsights'
import { SHORT_TRIP_KM } from './storyDay'
import { geohashDistanceKm } from '../utils/tripMap'
import type { ChargingBenchmark } from '../api/chargingBenchmarkService'

/**
 * Der Moment-Hero: was gerade passiert ist, als ein Satz mit einer Handlung. Kein Modell,
 * nur Ereignis, Verfall, "schon gesehen". Reine Funktionen ohne Vue, Gesehen-Speicher liegt
 * in useMomentSeen. Je Zustand ein detect…, jedes liefert null oder genau einen Kandidaten.
 */

export const HOUR = 3_600_000
export const DAY = 24 * HOUR

export type MomentKind =
  | 'expensive' | 'long_trip' | 'first_frost' | 'month_change' | 'battery' | 'rank' | 'silence' | 'gap' | 'milestone'

interface MomentBase {
  /** Zustand plus Ereignis-ID: Schluessel fuer "gesehen" und "weggeklickt". */
  key: string
  /** Zeitpunkt des Ereignisses in ms, Basis des Verfalls. */
  at: number
  /** Infinity: lebt, bis die Bedingung entfaellt. */
  lifetimeMs: number
  /** Es gibt etwas nachzutragen oder einzurichten. */
  actionable: boolean
  /** Bleibt trotz "gesehen", bis der Nutzer es wegklickt. */
  dismissible: boolean
}

export type MomentData =
  | { kind: 'expensive'; logId: string; cpoName: string; pricePerKwh: number; medianPricePerKwh: number; cardFromPricePerKwh: number | null }
  | { kind: 'long_trip'; km: number; stops: number; costEur: number | null; /** Ziel der letzten Fahrt, für die Wetterprognose */ endGeohash?: string | null }
  | { kind: 'first_frost'; tempC: number; winterKm: number; summerKm: number }
  | { kind: 'month_change'; year: number; month: number; km: number | null; eur: number | null; charges: number; petrolEur: number | null }
  | { kind: 'battery'; sohPercent: number; estimated: boolean; previousSohPercent: number | null; previousAt: number | null }
  | { kind: 'rank'; year: number; month: number; places: RankPlace[] }
  | { kind: 'silence'; days: number; kwh: number | null; autoSync: boolean }
  | { kind: 'gap'; logId: string; count: number; kwh: number | null; since: number }
  | { kind: 'milestone'; metric: MilestoneMetric; value: number; charges: number; km: number; co2Kg: number | null; /** Bitte ums Supporter-Pack mitzeigen */ support?: boolean }

export type Moment = MomentBase & MomentData

/** Ein Treppchenplatz im abgeschlossenen Monat. */
export interface RankPlace { category: string; rank: number; value: number; unit: string }

export interface MomentSeen {
  /** Schluessel -> erstmals als Karte gezeigt (ms), nur fuer das Zaehlen und das Konfetti. */
  shown: Record<string, number>
  /** Schluessel -> erledigt bis (ms). */
  dismissed: Record<string, number>
}

export interface MomentLog {
  id: string
  loggedAt: string
  createdAt?: string | null
  kwhCharged?: number | null
  kwhAtVehicle?: number | null
  costEur?: number | null
  socBeforeChargePercent?: number | null
  socAfterChargePercent?: number | null
  dataSource?: string | null
  /** "Weiß ich nicht mehr": nicht mehr nach dem Preis fragen. */
  priceUnknown?: boolean
  isPublicCharging?: boolean | null
  cpoName?: string | null
  chargingType?: string | null
}

export type MilestoneMetric = 'charges' | 'km' | 'co2' | 'years'

/** Ein Punkt der Statistik-Zeitreihe (chargesOverTime) ueber die ganze Lebenszeit. */
export interface MomentPoint {
  timestamp: string
  charges?: number
  distanceKm: number | null
  consumptionKwhPer100km: number | null
}

export interface MomentTrip {
  id: string
  tripStartedAt?: string | null
  tripEndedAt?: string | null
  distanceKm?: number | null
  outsideTempCelsius?: number | null
  locationStartGeohash?: string | null
  locationEndGeohash?: string | null
}

/** Juenger als die Zuletzt-Karte ("Heute geladen") ist keine Luecke. */
const JUST_CHARGED_MS = 2 * HOUR
const EXPENSIVE_MS = 3 * DAY
/** Ab so viel über dem Median anderer Fahrer gilt eine Ladung als zu teuer. */
const EXPENSIVE_FACTOR = 1.2
/** Manuell erfasst: zaehlt der Zeitpunkt des Eintragens, aber nur fuer Ladungen der letzten 24 h. */
const LONG_TRIP_KM = 250
/** Zielwetter nur, wenn das Ziel so weit (Luftlinie) vom Start liegt: Hin und zurück endet zu Hause. */
const FORECAST_MIN_KM = 200
const FROST_MS = 3 * DAY
const BATTERY_MS = 3 * DAY
const BATTERY_COMPARE_MIN_MS = 90 * DAY
const RANK_MAX = 3
const SILENCE_MS = 14 * DAY
/** Juenger gehoert die Ladung zu "gerade geladen", aelter ist sie kein Moment mehr. */
const GAP_MIN_MS = JUST_CHARGED_MS
const GAP_MAX_MS = 31 * DAY

const ms = (s: string | null | undefined): number | null => {
  if (!s) return null
  const t = Date.parse(s)
  return Number.isFinite(t) ? t : null
}

/** "2026-10-23" als Mitternacht Ortszeit, volle Zeitstempel unveraendert. */
const dateMs = (s: string): number => /^\d{4}-\d{2}-\d{2}$/.test(s)
  ? new Date(Number(s.slice(0, 4)), Number(s.slice(5, 7)) - 1, Number(s.slice(8, 10))).getTime()
  : Date.parse(s)

const base = (key: string, at: number, lifetimeMs: number, actionable = false, dismissible = false): MomentBase =>
  ({ key, at, lifetimeMs, actionable, dismissible })

/**
 * Die Ladung, für die sich ein Preisvergleich lohnt: die neueste öffentliche der letzten 3 Tage
 * mit Betreiber, AC oder DC und Preis. Preis je kWh wie die App je Ladung (an der Säule, sonst
 * am Fahrzeug) und wie das Backend den Vergleich rechnet.
 */
export function expensiveCandidate(logs: MomentLog[], now: number): { log: MomentLog; pricePerKwh: number } | null {
  for (const log of logs) {
    const at = ms(log.loggedAt)
    if (at == null || now - at > EXPENSIVE_MS) continue
    if (log.isPublicCharging !== true || !log.cpoName || (log.chargingType !== 'AC' && log.chargingType !== 'DC')) continue
    const basis = log.kwhCharged != null && log.kwhCharged > 0 ? log.kwhCharged : log.kwhAtVehicle
    if (log.costEur == null || log.costEur <= 0 || basis == null || basis <= 0) continue
    return { log, pricePerKwh: log.costEur / basis }
  }
  return null
}

/** Mehr als 20 % über dem, was andere Fahrer beim selben Betreiber zahlen. Eine Karte nur, wenn sie günstiger ist als der gezahlte Preis. */
export function detectExpensive(input: { logs: MomentLog[]; benchmark: ChargingBenchmark | null; now: number }): Moment | null {
  const { benchmark } = input
  const c = expensiveCandidate(input.logs, input.now)
  if (!c || !benchmark || benchmark.chargingType !== c.log.chargingType) return null
  if (c.pricePerKwh <= benchmark.medianPricePerKwh * EXPENSIVE_FACTOR) return null
  const card = benchmark.cards.map(k => k.pricePerKwh).filter(p => p < c.pricePerKwh).sort((a, b) => a - b)[0] ?? null
  return {
    ...base(`expensive:${c.log.id}`, ms(c.log.loggedAt)!, EXPENSIVE_MS),
    kind: 'expensive',
    logId: c.log.id,
    cpoName: benchmark.cpoName,
    pricePerKwh: c.pricePerKwh,
    medianPricePerKwh: benchmark.medianPricePerKwh,
    cardFromPricePerKwh: card,
  }
}

export function detectLongTrip(input: { trips: MomentTrip[]; logs: MomentLog[]; now: number }): Moment | null {
  const { now } = input
  const recent = input.trips.filter(t => {
    const end = ms(tripTimestamp(t))
    return end != null && end <= now && now - end <= DAY && (t.distanceKm ?? 0) >= SHORT_TRIP_KM
  })
  const km = recent.reduce((s, t) => s + (t.distanceKm ?? 0), 0)
  if (km <= LONG_TRIP_KM) return null
  const starts = recent.map(t => ms(t.tripStartedAt) ?? ms(tripTimestamp(t))!)
  const ends = recent.map(t => ms(tripTimestamp(t))!)
  const from = Math.min(...starts)
  const to = Math.max(...ends)
  const stops = input.logs.filter(l => { const t = ms(l.loggedAt); return t != null && t >= from && t <= to })
  const costEur = stops.length > 0 && stops.every(l => l.costEur != null)
    ? stops.reduce((s, l) => s + l.costEur!, 0)
    : null
  const last = recent.find(t => ms(tripTimestamp(t)) === to)
  const first = recent.find(t => (ms(t.tripStartedAt) ?? ms(tripTimestamp(t))) === from)
  const away = geohashDistanceKm(first?.locationStartGeohash, last?.locationEndGeohash)
  const d = new Date(to)
  return {
    ...base(`long_trip:${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`, to, DAY),
    kind: 'long_trip', km, stops: stops.length, costEur, endGeohash: away != null && away >= FORECAST_MIN_KM ? last!.locationEndGeohash! : null,
  }
}

/** Saison ab August: Januar 2027 gehoert zum Winter 2026. */
const seasonOf = (t: number) => { const d = new Date(t); return d.getMonth() >= 7 ? d.getFullYear() : d.getFullYear() - 1 }

export function detectFirstFrost(input: {
  trips: MomentTrip[]
  now: number
  range: { winter: { km: number } | null; summer: { km: number } | null } | null
}): Moment | null {
  const winter = input.range?.winter
  const summer = input.range?.summer
  if (!winter || !summer || winter.km >= summer.km) return null
  const cold = input.trips
    .map(t => ({ end: ms(tripTimestamp(t)), temp: t.outsideTempCelsius }))
    .filter((x): x is { end: number; temp: number } =>
      x.end != null && x.temp != null && x.temp < COLD_MAX_C && x.end <= input.now && input.now - x.end <= FROST_MS)
  if (cold.length === 0) return null
  const at = Math.min(...cold.map(x => x.end))
  return {
    // Einmal je Saison: der Schluessel kennt kein Datum, "gesehen" haelt bis zum naechsten Herbst.
    ...base(`first_frost:${seasonOf(at)}`, at, FROST_MS),
    kind: 'first_frost',
    tempC: Math.min(...cold.map(x => x.temp)),
    winterKm: winter.km,
    summerKm: summer.km,
  }
}

export function detectMonthChange(input: {
  now: number
  previous: { year: number; month: number; distanceKm: number | null; energyCostEur: number | null; charges: number; priceless: number } | null
  petrolEurPerLitre: number
  /** Benziner der Fahrzeugklasse, l/100 km */
  petrolLitersPer100km: number
}): Moment | null {
  const p = input.previous
  const d = new Date(input.now)
  if (!p || p.charges <= 0) return null
  const line = heroLine(p)
  const petrol = petrolCostEur(line.km, input.petrolEurPerLitre, input.petrolLitersPer100km)
  // Benzinvergleich nur mit vollstaendigen Preisen und nur, wenn er etwas zeigt (wie im Monats-Hero).
  const petrolEur = line.kind === 'km_cost' && p.priceless === 0 && petrol != null && petrol > line.eur! ? petrol : null
  return {
    // Im Folgemonat beim ersten Oeffnen, egal an welchem Tag, bis erledigt.
    ...base(`month_change:${p.year}-${p.month}`, new Date(d.getFullYear(), d.getMonth(), 1).getTime(), Infinity, false, true),
    kind: 'month_change', year: p.year, month: p.month, km: line.km, eur: line.eur, charges: p.charges, petrolEur,
  }
}

export function detectBattery(input: {
  history: Array<{ id: string; recordedAt: string; sohPercent: number; source: string }>
  now: number
}): Moment | null {
  const sorted = [...input.history]
    .map(e => ({ ...e, t: dateMs(e.recordedAt) }))
    .filter(e => Number.isFinite(e.t))
    .sort((a, b) => b.t - a.t)
  const latest = sorted[0]
  if (!latest || input.now - latest.t > BATTERY_MS || latest.t > input.now) return null
  const previous = sorted.find(e => latest.t - e.t >= BATTERY_COMPARE_MIN_MS) ?? null
  return {
    ...base(`battery:${latest.id}`, latest.t, BATTERY_MS),
    kind: 'battery',
    sohPercent: latest.sohPercent,
    // Nur die eigene Angabe ist ein Messwert, alles Berechnete bleibt eine Schaetzung.
    estimated: latest.source !== 'MANUAL',
    previousSohPercent: previous?.sohPercent ?? null,
    previousAt: previous?.t ?? null,
  }
}

/** Rang hat keinen Ereigniszeitpunkt: er gilt ab jetzt, "gesehen" verhindert die Wiederholung. */
/**
 * Ranglisten-Abschluss: alle Treppchenplaetze des Vormonats in einer Karte, im Folgemonat beim
 * ersten Oeffnen, egal an welchem Tag, bis erledigt. Im laufenden Monat gibt es keine Rang-Karte.
 */
export function detectRankClosing(input: { ranks: Array<{ category: string; ownEntry: RankEntry | null } | null>; now: number }): Moment | null {
  const places: RankPlace[] = []
  for (const r of input.ranks) {
    if (r?.ownEntry && r.ownEntry.rank <= RANK_MAX) places.push({ category: r.category, rank: r.ownEntry.rank, value: r.ownEntry.value, unit: r.ownEntry.unit })
  }
  if (places.length === 0) return null
  places.sort((a, b) => a.rank - b.rank)
  const d = new Date(input.now)
  const prev = new Date(d.getFullYear(), d.getMonth() - 1, 1)
  return {
    ...base(`rank:${prev.getFullYear()}-${prev.getMonth() + 1}`, new Date(d.getFullYear(), d.getMonth(), 1).getTime(), Infinity, false, true),
    kind: 'rank', year: prev.getFullYear(), month: prev.getMonth() + 1, places,
  }
}

/** Nur fuer Hand-Erfasser: kommt die letzte Ladung aus einer Verbindung, heisst Stille "nicht geladen". */
export function detectSilence(input: { log: MomentLog | null; now: number; autoSync: boolean }): Moment | null {
  const { log, now } = input
  const logged = ms(log?.loggedAt)
  if (!log || logged == null || log.dataSource !== 'USER_LOGGED' || now - logged <= SILENCE_MS) return null
  return {
    ...base(`silence:${log.id}`, logged + SILENCE_MS, Infinity, true, true),
    kind: 'silence',
    days: Math.floor((now - logged) / DAY),
    kwh: log.kwhAtVehicle ?? log.kwhCharged ?? null,
    autoSync: input.autoSync,
  }
}

/**
 * Ladungen ohne Preis aus dem letzten Monat. Eine: Frage nach genau ihr, mit "Weiß ich nicht mehr".
 * Mehrere: die Zahl, nachgetragen wird in der Liste. skipLogId: die Ladung von "gerade geladen".
 */
export function detectGap(input: { logs: MomentLog[]; now: number; skipLogId?: string | null }): Moment | null {
  const { now } = input
  const open = input.logs
    .filter(l => l.costEur == null && !l.priceUnknown && l.id !== input.skipLogId)
    .map(l => ({ log: l, t: ms(l.loggedAt) }))
    .filter((x): x is { log: MomentLog; t: number } => x.t != null && now - x.t >= GAP_MIN_MS && now - x.t <= GAP_MAX_MS)
    .sort((a, b) => b.t - a.t)
  if (open.length === 0) return null
  const newest = open[0]
  return {
    ...base(`gap:${newest.log.id}`, newest.t, Infinity, true),
    kind: 'gap',
    logId: newest.log.id,
    count: open.length,
    kwh: newest.log.kwhAtVehicle ?? newest.log.kwhCharged ?? null,
    since: open[open.length - 1].t,
  }
}

const MILESTONE_MS = 3 * DAY
const MILESTONE_STEP: Record<Exclude<MilestoneMetric, 'years'>, number> = { charges: 100, km: 10_000, co2: 1000 }

/** Die Schwelle, die zwischen vorher und nachher ueberschritten wurde, sonst null. */
function crossedStep(before: number, after: number, step: number): number | null {
  const reached = Math.floor(after / step)
  return reached >= 1 && reached > Math.floor(before / step) ? reached * step : null
}

/**
 * Meilensteine aus der Lebenszeit-Reihe: 100 Ladungen, 10.000 km, 1 t CO₂ weniger als mit Benzin
 * (wie die CO₂-Kachel aus Strecke und Verbrauch), Jahrestag der ersten Ladung. Zaehlt der Tag,
 * an dem die Schwelle fiel; mehrere in 3 Tagen: der juengste.
 */
export function detectMilestone(input: { series: MomentPoint[]; now: number; petrolLitersPer100km: number }): Moment | null {
  const { now } = input
  const points = input.series
    .map(p => ({ p, t: ms(p.timestamp) }))
    .filter((x): x is { p: MomentPoint; t: number } => x.t != null && x.t <= now)
    .sort((a, b) => a.t - b.t)
  if (points.length === 0) return null

  const total = { charges: 0, km: 0, co2: 0 }
  const hits: Array<{ metric: MilestoneMetric; value: number; at: number }> = []
  for (const { p, t } of points) {
    const before = { ...total }
    const km = p.distanceKm ?? 0
    total.charges += p.charges ?? 0
    total.km += km
    total.co2 += co2SavedKg(p, input.petrolLitersPer100km)
    if (now - t > MILESTONE_MS) continue
    for (const metric of ['charges', 'km', 'co2'] as const) {
      const value = crossedStep(before[metric], total[metric], MILESTONE_STEP[metric])
      if (value != null) hits.push({ metric, value, at: t })
    }
  }

  const first = new Date(points[0].t)
  let years = new Date(now).getFullYear() - first.getFullYear()
  const anniversary = (n: number) => { const d = new Date(first); d.setFullYear(first.getFullYear() + n); return d.getTime() }
  if (anniversary(years) > now) years--
  if (years >= 1 && now - anniversary(years) <= MILESTONE_MS) hits.push({ metric: 'years', value: years, at: anniversary(years) })

  if (hits.length === 0) return null
  const hit = hits.reduce((a, b) => (b.at >= a.at ? b : a))
  return {
    ...base(`milestone:${hit.metric}:${hit.value}`, hit.at, MILESTONE_MS),
    kind: 'milestone',
    metric: hit.metric,
    value: hit.value,
    charges: total.charges,
    km: Math.round(total.km),
    co2Kg: total.co2 > 0 ? total.co2 : null,
  }
}

const BASE: Record<MomentKind, number> = {
  expensive: 85, long_trip: 80, first_frost: 75, month_change: 70, milestone: 65, battery: 60, rank: 55, silence: 50, gap: 40,
}
const ACTION_BONUS = 20

function expired(m: Moment, now: number, seen: MomentSeen): boolean {
  if ((seen.dismissed[m.key] ?? 0) > now) return true
  return Number.isFinite(m.lifetimeMs) && now - m.at > m.lifetimeMs
}

/** Basis x Verfall + Handlungs-Bonus. Erledigt zaehlt nur das Geraet; gesehen nimmt nichts weg. */
export function momentScore(m: Moment, now: number): number {
  const age = Math.max(0, now - m.at)
  const decay = Number.isFinite(m.lifetimeMs) ? Math.max(0, 1 - age / m.lifetimeMs) : 1
  return BASE[m.kind] * decay + (m.actionable ? ACTION_BONUS : 0)
}

export interface ScoredMoment { moment: Moment; score: number }

/** Die Karten von unten: alles, was lebt und nicht erledigt ist, beste zuerst. */
export function pickCards(candidates: Moment[], now: number, seen: MomentSeen): ScoredMoment[] {
  return candidates
    .filter(m => !expired(m, now, seen))
    .map(moment => ({ moment, score: momentScore(moment, now) }))
    .sort((a, b) => b.score - a.score)
}
