import { tripTimestamp } from '../utils/recentActivity'
import { heroLine, petrolCostEur, type StoryFeedItem } from './storyModel'
import { COLD_MAX_C, type RankResult } from './storyInsights'

/**
 * Der Moment-Hero: was gerade passiert ist, als ein Satz mit einer Handlung. Kein Modell,
 * nur Ereignis, Verfall, "schon gesehen". Reine Funktionen ohne Vue, Gesehen-Speicher liegt
 * in useMomentSeen. Je Zustand ein detect…, jedes liefert null oder genau einen Kandidaten.
 */

export const HOUR = 3_600_000
export const DAY = 24 * HOUR

export type MomentKind =
  | 'live' | 'just_charged' | 'long_trip' | 'first_frost' | 'month_change' | 'battery' | 'rank' | 'silence' | 'gap' | 'month'

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
  | { kind: 'live' }
  | { kind: 'month' }
  | { kind: 'just_charged'; logId: string; kwh: number | null; socFrom: number | null; socTo: number | null; rangeKm: number | null; costEur: number | null }
  | { kind: 'long_trip'; km: number; stops: number; costEur: number | null }
  | { kind: 'first_frost'; tempC: number; winterKm: number; summerKm: number }
  | { kind: 'month_change'; year: number; month: number; km: number | null; eur: number | null; charges: number; petrolEur: number | null }
  | { kind: 'battery'; sohPercent: number; estimated: boolean; previousSohPercent: number | null; previousAt: number | null }
  | { kind: 'rank'; category: string; rank: number }
  | { kind: 'silence'; days: number; kwh: number | null; autoSync: boolean }
  | { kind: 'gap'; logId: string; count: number; kwh: number | null; since: number }

export type Moment = MomentBase & MomentData

export interface MomentSeen {
  /** Schluessel -> erstmals als Hero gezeigt (ms). */
  shown: Record<string, number>
  /** Schluessel -> weggeklickt bis (ms). */
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
}

export interface MomentTrip {
  id: string
  tripStartedAt?: string | null
  tripEndedAt?: string | null
  distanceKm?: number | null
  outsideTempCelsius?: number | null
}

const JUST_CHARGED_MS = 2 * HOUR
/** Manuell erfasst: zaehlt der Zeitpunkt des Eintragens, aber nur fuer Ladungen der letzten 24 h. */
const MANUAL_RECENT_MS = DAY
const LONG_TRIP_KM = 250
const FROST_MS = 3 * DAY
const MONTH_CHANGE_DAYS = 5
const BATTERY_MS = 3 * DAY
const BATTERY_COMPARE_MIN_MS = 90 * DAY
const RANK_MAX = 3
const RANK_MS = 7 * DAY
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

export function detectLive(active: boolean): Moment | null {
  return active ? { ...base('live', 0, Infinity), kind: 'live' } : null
}

export function monthMoment(): Moment {
  return { ...base('month', 0, Infinity), kind: 'month' }
}

export function detectJustCharged(input: { log: MomentLog | null; now: number; consumptionKwh100: number | null }): Moment | null {
  const { log, now } = input
  const logged = ms(log?.loggedAt)
  if (!log || logged == null) return null
  const created = ms(log.createdAt)
  const manualRecent = log.dataSource === 'USER_LOGGED' && created != null && now - logged <= MANUAL_RECENT_MS
  const at = manualRecent ? Math.max(logged, created!) : logged
  if (now - at > JUST_CHARGED_MS) return null
  const kwh = log.kwhAtVehicle ?? log.kwhCharged ?? null
  const from = log.socBeforeChargePercent ?? null
  const to = log.socAfterChargePercent ?? null
  const socOk = from != null && to != null && to > from
  const c = input.consumptionKwh100
  return {
    ...base(`just_charged:${log.id}`, at, JUST_CHARGED_MS, log.costEur == null),
    kind: 'just_charged',
    logId: log.id,
    kwh,
    socFrom: socOk ? from : null,
    socTo: socOk ? to : null,
    // Reichweite der geladenen Energie mit dem Verbrauch der letzten 4 Wochen (wie die Reichweiten-Kachel).
    rangeKm: kwh != null && c != null && c > 0 ? Math.round(kwh / c * 100) : null,
    costEur: log.costEur ?? null,
  }
}

export function detectLongTrip(input: { trips: MomentTrip[]; logs: MomentLog[]; now: number }): Moment | null {
  const { now } = input
  const recent = input.trips.filter(t => {
    const end = ms(tripTimestamp(t))
    return end != null && end <= now && now - end <= DAY && (t.distanceKm ?? 0) > 0
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
  const d = new Date(to)
  return {
    ...base(`long_trip:${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`, to, DAY),
    kind: 'long_trip', km, stops: stops.length, costEur,
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
}): Moment | null {
  const p = input.previous
  const d = new Date(input.now)
  if (!p || p.charges <= 0 || d.getDate() > MONTH_CHANGE_DAYS) return null
  const line = heroLine(p)
  const petrol = petrolCostEur(line.km, input.petrolEurPerLitre)
  // Benzinvergleich nur mit vollstaendigen Preisen und nur, wenn er etwas zeigt (wie im Monats-Hero).
  const petrolEur = line.kind === 'km_cost' && p.priceless === 0 && petrol != null && petrol > line.eur! ? petrol : null
  return {
    ...base(`month_change:${p.year}-${p.month}`, new Date(d.getFullYear(), d.getMonth(), 1).getTime(), MONTH_CHANGE_DAYS * DAY),
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
export function detectRank(input: { rank: RankResult | null; now: number }): Moment | null {
  const r = input.rank
  if (!r || r.rank > RANK_MAX) return null
  const d = new Date(input.now)
  return {
    ...base(`rank:${r.category}:${r.rank}:${d.getFullYear()}-${d.getMonth() + 1}`, input.now, RANK_MS),
    kind: 'rank', category: r.category, rank: r.rank,
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

const BASE: Record<MomentKind, number> = {
  live: 1000, just_charged: 90, long_trip: 80, first_frost: 75, month_change: 70, battery: 60, rank: 55, silence: 50, gap: 40, month: 10,
}
const ACTION_BONUS = 20
/** Gross genug, dass ein gesehener Moment unter den Monat faellt. */
const SEEN_PENALTY = 100
/** Innerhalb der Schonfrist bleibt der Hero stehen (zurueck zur Seite, Pull-to-refresh). */
export const SEEN_GRACE_MS = 30 * 60_000

function expired(m: Moment, now: number, seen: MomentSeen): boolean {
  if ((seen.dismissed[m.key] ?? 0) > now) return true
  return Number.isFinite(m.lifetimeMs) && now - m.at > m.lifetimeMs
}

/** Basis x Verfall + Handlungs-Bonus - Gesehen-Abzug. Live und Monat verfallen nie. */
export function momentScore(m: Moment, now: number, seen: MomentSeen): number {
  const age = Math.max(0, now - m.at)
  const decay = Number.isFinite(m.lifetimeMs) ? Math.max(0, 1 - age / m.lifetimeMs) : 1
  const shownAt = seen.shown[m.key]
  const penalty = !m.dismissible && m.kind !== 'live' && m.kind !== 'month' && shownAt != null && now - shownAt > SEEN_GRACE_MS
    ? SEEN_PENALTY : 0
  return BASE[m.kind] * decay + (m.actionable ? ACTION_BONUS : 0) - penalty
}

export interface ScoredMoment { moment: Moment; score: number }

/**
 * Bester Kandidat wird Hero, der Monat ist immer dabei. Der Rest (ohne Live und Monat) sind
 * die Momente, die den Monat schlagen; sie konkurrieren im Feed mit.
 */
export function pickMoment(candidates: Moment[], now: number, seen: MomentSeen): { hero: Moment; rest: ScoredMoment[] } {
  const all = candidates.some(c => c.kind === 'month') ? candidates : [...candidates, monthMoment()]
  const scored = all
    .filter(m => !expired(m, now, seen))
    .map(moment => ({ moment, score: momentScore(moment, now, seen) }))
    .sort((a, b) => b.score - a.score)
  const hero = scored[0].moment
  const floor = BASE.month
  const rest = scored.slice(1).filter(s => s.moment.kind !== 'month' && s.moment.kind !== 'live' && s.score > floor)
  return { hero, rest }
}

/** Fahrerfrage je Moment im Feed (1 Kosten, 2 Realitaet, 3 Community, 4 Gesundheit). */
const QUESTION: Partial<Record<MomentKind, StoryFeedItem['question']>> = {
  just_charged: 1, long_trip: 2, first_frost: 2, month_change: 1, battery: 4, silence: 1, gap: 1,
}

/** Rang fehlt bewusst: den nennt der Feed schon als community_rank. */
export function momentFeedItems(rest: ScoredMoment[]): StoryFeedItem[] {
  return rest
    .filter(s => QUESTION[s.moment.kind] != null)
    .map(s => ({
      id: 'moment' as const,
      question: QUESTION[s.moment.kind]!,
      relevance: Math.min(100, s.score),
      sentiment: 'neutral' as const,
      params: {},
      moment: s.moment,
    }))
}
