/**
 * Reine Ableitung des Story-Dashboards aus vorhandenen Statistiken.
 *
 * Keine Vue-Abhaengigkeit, keine Formatierung, keine i18n: hier entstehen nur Zahlen,
 * Vorzeichen und Reihenfolge. Saetze und Einheiten baut die Komponente aus i18n-Keys.
 */
import type { StatisticsData, ChargeDataPoint } from './useDashboardStats'
import { VERBRENNER_G_PER_KM, STROMMIX_G_PER_KWH, OEKO_G_PER_KWH } from '../components/dashboard/co2'
import type { EnergyFlow, RangeEstimate } from './storyEnergy'
import type { WhatIfModel, RankResult, DataQuality, TemperatureEffect } from './storyInsights'

export type Sentiment = 'good' | 'warn' | 'bad' | 'neutral'
/** Feste Reihenfolge der Kacheln, siehe TILE_ORDER. */
export type TileId = 'cost' | 'flow' | 'range' | 'wltp' | 'peer' | 'battery' | 'co2'
export type SparkVariant = 'line' | 'bars' | 'range'

export interface StoryTile {
  id: TileId
  value: number
  /** Zweiter Wert, z. B. CO2 mit Oekostrom. */
  valueGreen?: number
  /** Absolutes Delta (Kosten in Euro) gegen den Vormonat. */
  delta: number | null
  /** Relatives Delta in Prozent (WLTP, Peer). */
  deltaPercent: number | null
  sentiment: Sentiment
  spark: number[]
  sparkVariant: SparkVariant
  /** Referenzlinie fuer die Sparkline (WLTP-Wert). */
  reference?: number
  peerCount?: number
  odometerKm?: number | null
  /** Rohdaten fuer den Mini-Sankey (nur id 'flow'). */
  flow?: EnergyFlow
  /** Rohdaten fuer die Akku-Silhouette (nur id 'range'). */
  range?: RangeEstimate
  /** Gesetzt, wenn der Monatswert auf weniger als MIN_SOLID_CHARGES Ladungen beruht: die Kachel nennt die Zahl. */
  thinCharges?: number
}

export type FeedId =
  | 'consumption_trend' | 'home_savings' | 'most_expensive' | 'peer_position' | 'phantom_locked' | 'cost_trend'
  | 'what_if_model' | 'community_rank' | 'data_quality' | 'temperature_effect' | 'temperature_locked'

export interface StoryFeedItem {
  id: FeedId
  /** Nummer der Fahrerfrage (1 Kosten, 2 Realitaet, 3 Community, 4 Gesundheit, 5 Oeko). */
  question: 1 | 2 | 3 | 4 | 5
  relevance: number
  sentiment: Sentiment
  locked?: boolean
  /** Rohwerte fuer den Satz, die Komponente formatiert sie. */
  params: Record<string, number>
  /** Textschluessel ohne Zahl, z. B. die Leaderboard-Kategorie. */
  label?: string
}

export interface StoryInput {
  month: StatisticsData
  previousMonth: StatisticsData | null
  /** Sechs Monatswerte, aeltester zuerst, letzter ist der gewaehlte Monat. */
  monthlyCosts: number[]
  monthlyConsumption: number[]
  wltpConsumption: number | null
  sohPercent: number | null
  odometerKm: number | null
  savingsEur: number | null
  amortisationYearsRemaining: number | null
  energySplitLocked: boolean
  flow: EnergyFlow | null
  range: RangeEstimate | null
  /** Aufsteigend nach Kosten, Nutzermodell markiert. Lifetime-Daten. */
  whatIf: WhatIfModel[]
  rank: RankResult | null
  quality: DataQuality | null
  temperature: TemperatureEffect | null
  /** True, wenn Fahrten-Analytik gesperrt ist und kein Temperatur-Effekt vorliegt. */
  temperatureLocked: boolean
}

export interface DayCoverage {
  day: number
  hasData: boolean
  inLongestGap: boolean
}

/** Angenommener Benziner: 7,5 l/100 km, passend zu VERBRENNER_G_PER_KM. */
export const PETROL_L_PER_100KM = 7.5
/** Rueckfall-Literpreis Super (E5/E10-Mittel, Tankerkoenig-Bundesschnitt 02.10.2026), wenn die Preis-API nicht antwortet. */
export const PETROL_EUR_PER_L_DE = 2.13

const WLTP_WARN_PERCENT = 5
const PEER_NEUTRAL_PERCENT = 1
const EXPENSIVE_FACTOR = 1.6
/** Unter diesen Schwellen gibt es keinen Feed-Satz: "0 € weniger" ist keine Erkenntnis. */
/** Unter drei Ladungen im Monat zeigt die Kachel, worauf der Wert beruht. Reichweite ist davon frei, sie rechnet mit 4 Wochen. */
export const MIN_SOLID_CHARGES = 3
const MIN_COST_DIFF_EUR = 1
const MIN_CONSUMPTION_DIFF = 0.2
/** Ladeverlust: bis 12 Prozent ueblich (AC-Pauschale 10), bis 20 auffaellig, darueber Problem. */
const FLOW_LOSS_GOOD_PERCENT = 12
const FLOW_LOSS_WARN_PERCENT = 20
/** Ein Fremdmodell muss mindestens so viel guenstiger sein, damit es einen Satz wert ist. */
const WHAT_IF_MIN_SAVING_PERCENT = 5
const FEED_MAX = 5

export function petrolCostEur(distanceKm: number | null, eurPerLitre: number): number | null {
  if (distanceKm == null || distanceKm <= 0) return null
  return (distanceKm / 100) * PETROL_L_PER_100KM * eurPerLitre
}

export function co2Comparison(distanceKm: number, consumptionKwhPer100km: number) {
  const kwh = (distanceKm / 100) * consumptionKwhPer100km
  return {
    petrolKg: (distanceKm * VERBRENNER_G_PER_KM) / 1000,
    gridKg: (kwh * STROMMIX_G_PER_KWH) / 1000,
    greenKg: (kwh * OEKO_G_PER_KWH) / 1000,
  }
}

function pct(user: number, ref: number): number {
  return ((user - ref) / ref) * 100
}

export function buildStoryTiles(input: StoryInput): StoryTile[] {
  const m = input.month
  const tiles: StoryTile[] = []

  // Stromkosten, nicht Gesamtkosten: Fixkosten fallen zeitanteilig oder einmalig an und wuerden
  // den Monatsvergleich dominieren (eine Reifenrechnung ist kein Fahrverhalten). Fixkosten stehen
  // in den Hero-Schichten und im Kosten-Sheet.
  const prevCost = input.previousMonth?.energyCostEur ?? null
  const costDelta = prevCost == null ? null : m.energyCostEur - prevCost
  tiles.push({
    id: 'cost',
    value: m.energyCostEur,
    delta: costDelta,
    deltaPercent: null,
    sentiment: costDelta == null || Math.abs(costDelta) < MIN_COST_DIFF_EUR ? 'neutral' : costDelta <= 0 ? 'good' : 'warn',
    spark: input.monthlyCosts,
    sparkVariant: 'line',
  })

  if (input.flow) {
    const p = input.flow.lossPercent
    tiles.push({
      id: 'flow',
      value: p,
      delta: null,
      deltaPercent: null,
      sentiment: p <= FLOW_LOSS_GOOD_PERCENT ? 'good' : p <= FLOW_LOSS_WARN_PERCENT ? 'warn' : 'bad',
      spark: [input.flow.gridKwh, input.flow.vehicleKwh],
      sparkVariant: 'range',
      flow: input.flow,
    })
  }

  if (input.range) {
    tiles.push({
      id: 'range',
      value: input.range.km,
      delta: null,
      deltaPercent: null,
      sentiment: 'neutral',
      spark: input.range.windows.map(w => w.km),
      sparkVariant: 'bars',
      range: input.range,
    })
  }

  const hasDistance = m.totalDistanceKm != null && m.totalDistanceKm > 0 && m.avgConsumptionKwhPer100km != null
  if (hasDistance && input.wltpConsumption != null && input.wltpConsumption > 0) {
    const d = pct(m.avgConsumptionKwhPer100km!, input.wltpConsumption)
    tiles.push({
      id: 'wltp',
      value: m.avgConsumptionKwhPer100km!,
      delta: null,
      deltaPercent: d,
      sentiment: d > WLTP_WARN_PERCENT ? 'warn' : 'good',
      spark: input.monthlyConsumption,
      sparkVariant: 'line',
      reference: input.wltpConsumption,
    })
  }

  const pb = m.peerBenchmark
  if (pb && pb.userPeriodConsumptionKwhPer100km != null && pb.peerAvgConsumptionKwhPer100km != null && pb.uniquePeerUsers > 0) {
    const d = pct(pb.userPeriodConsumptionKwhPer100km, pb.peerAvgConsumptionKwhPer100km)
    tiles.push({
      id: 'peer',
      value: pb.userPeriodConsumptionKwhPer100km,
      delta: null,
      deltaPercent: d,
      sentiment: Math.abs(d) <= PEER_NEUTRAL_PERCENT ? 'neutral' : d < 0 ? 'good' : 'warn',
      spark: [pb.peerAvgConsumptionKwhPer100km, pb.userPeriodConsumptionKwhPer100km],
      sparkVariant: 'range',
      peerCount: pb.uniquePeerUsers,
    })
  }

  if (input.sohPercent != null) {
    tiles.push({
      id: 'battery',
      value: input.sohPercent,
      delta: null,
      deltaPercent: null,
      sentiment: input.sohPercent >= 90 ? 'good' : input.sohPercent >= 80 ? 'warn' : 'bad',
      spark: [100, input.sohPercent],
      sparkVariant: 'range',
      odometerKm: input.odometerKm,
    })
  }

  if (hasDistance) {
    const c = co2Comparison(m.totalDistanceKm!, m.avgConsumptionKwhPer100km!)
    tiles.push({
      id: 'co2',
      value: c.petrolKg - c.gridKg,
      valueGreen: c.petrolKg - c.greenKg,
      delta: null,
      deltaPercent: null,
      sentiment: 'good',
      spark: [c.gridKg, c.greenKg, c.petrolKg],
      sparkVariant: 'bars',
    })
  }

  const thin = m.totalCharges > 0 && m.totalCharges < MIN_SOLID_CHARGES ? m.totalCharges : undefined
  if (thin != null) {
    for (const t of tiles) if (t.id === 'cost' || t.id === 'wltp' || t.id === 'peer') t.thinCharges = thin
  }
  return tiles
}

export function buildStoryFeed(input: StoryInput): StoryFeedItem[] {
  const m = input.month
  const items: StoryFeedItem[] = []

  const prev = input.previousMonth
  if (prev?.avgConsumptionKwhPer100km != null && m.avgConsumptionKwhPer100km != null
      && Math.abs(m.avgConsumptionKwhPer100km - prev.avgConsumptionKwhPer100km) >= MIN_CONSUMPTION_DIFF) {
    const diff = m.avgConsumptionKwhPer100km - prev.avgConsumptionKwhPer100km
    items.push({
      id: 'consumption_trend',
      question: 2,
      relevance: Math.min(100, 40 + Math.abs(diff) * 30),
      sentiment: diff <= 0 ? 'good' : 'warn',
      params: { diff, current: m.avgConsumptionKwhPer100km, previous: prev.avgConsumptionKwhPer100km },
    })
  }

  if (prev && prev.energyCostEur > 0 && Math.abs(m.energyCostEur - prev.energyCostEur) >= MIN_COST_DIFF_EUR) {
    const diff = m.energyCostEur - prev.energyCostEur
    items.push({
      id: 'cost_trend',
      question: 1,
      relevance: Math.min(90, 30 + Math.abs(diff)),
      sentiment: diff <= 0 ? 'good' : 'warn',
      params: { diff, current: m.energyCostEur, previous: prev.energyCostEur },
    })
  }

  if (m.totalCharges > 1 && m.avgCostPerKwh > 0 && m.totalKwhCharged > 0) {
    const avgCharge = (m.totalKwhCharged / m.totalCharges) * m.avgCostPerKwh
    if (m.mostExpensiveChargeEur > avgCharge * EXPENSIVE_FACTOR) {
      items.push({
        id: 'most_expensive',
        question: 1,
        relevance: Math.min(95, 50 + (m.mostExpensiveChargeEur / avgCharge) * 10),
        sentiment: 'warn',
        params: { eur: m.mostExpensiveChargeEur, avgEur: avgCharge },
      })
    }
  }

  if (input.savingsEur != null && input.savingsEur > 0) {
    items.push({
      id: 'home_savings',
      question: 1,
      relevance: 70,
      sentiment: 'good',
      params: {
        eur: input.savingsEur,
        monthsRemaining: input.amortisationYearsRemaining == null ? -1 : Math.round(input.amortisationYearsRemaining * 12),
      },
    })
  }

  const pb = m.peerBenchmark
  if (pb?.userPeriodCostPerKwh != null && pb.peerAvgCostPerKwh != null && pb.uniquePeerUsers > 0) {
    const diff = pb.userPeriodCostPerKwh - pb.peerAvgCostPerKwh
    items.push({
      id: 'peer_position',
      question: 3,
      relevance: 45 + Math.min(40, Math.abs(diff) * 400),
      sentiment: diff <= 0 ? 'good' : 'warn',
      params: { diffCt: diff * 100, peers: pb.uniquePeerUsers },
    })
  }

  const cheapest = input.whatIf.find(w => !w.isUser)
  const own = input.whatIf.find(w => w.isUser)
  if (cheapest && cheapest.deltaPercent <= -WHAT_IF_MIN_SAVING_PERCENT) {
    // Eigener Wert: aus der Liste, sonst aus dem Delta zurueckgerechnet.
    const mine = own?.costPer100km ?? cheapest.costPer100km / (1 + cheapest.deltaPercent / 100)
    items.push({
      id: 'what_if_model',
      question: 3,
      relevance: 40 + Math.min(35, Math.abs(cheapest.deltaPercent)),
      sentiment: 'neutral',
      params: { deltaPercent: cheapest.deltaPercent, modelIndex: input.whatIf.indexOf(cheapest), cars: cheapest.cars, mine, theirs: cheapest.costPer100km },
    })
  } else if (own && input.whatIf[0] === own && input.whatIf.length > 1) {
    items.push({
      id: 'what_if_model',
      question: 3,
      relevance: 50,
      sentiment: 'good',
      params: { deltaPercent: 0, modelIndex: -1, cars: input.whatIf.length - 1, mine: own.costPer100km, theirs: input.whatIf[1].costPer100km },
    })
  }

  if (input.rank) {
    items.push({
      id: 'community_rank',
      question: 3,
      relevance: Math.max(55, 90 - (input.rank.rank - 1) * 4),
      sentiment: input.rank.rank <= 3 ? 'good' : 'neutral',
      params: { rank: input.rank.rank, value: input.rank.value },
      label: input.rank.category,
    })
  }

  const q = input.quality
  if (q && q.severity !== 'ok') {
    items.push({
      id: 'data_quality',
      question: 1,
      relevance: q.severity === 'bad' ? 80 : 35,
      sentiment: q.severity,
      params: { estimated: q.estimated, total: q.total, priceless: q.priceless },
    })
  }

  const temp = input.temperature
  if (temp?.deltaPercent != null) {
    items.push({
      id: 'temperature_effect',
      question: 2,
      relevance: Math.min(90, 45 + Math.abs(temp.deltaPercent)),
      sentiment: 'neutral',
      params: { deltaPercent: temp.deltaPercent, cold: temp.coldKwh100 ?? 0, mild: temp.mildKwh100 ?? 0 },
    })
  }

  const ranked = items.sort((a, b) => b.relevance - a.relevance)
  // Hoechstens ein gesperrter Eintrag, immer zuletzt: ein Upsell verdraengt nie echte Erkenntnisse.
  const locked: StoryFeedItem | null = input.energySplitLocked
    ? { id: 'phantom_locked', question: 4, relevance: 0, sentiment: 'neutral', locked: true, params: {} }
    : input.temperatureLocked && !temp
      ? { id: 'temperature_locked', question: 2, relevance: 0, sentiment: 'neutral', locked: true, params: {} }
      : null
  if (!locked) return ranked.slice(0, FEED_MAX)
  return [...ranked.slice(0, FEED_MAX - 1), locked]
}

export function monthWindow(year: number, month: number, offset = 0): { start: string; end: string } {
  const first = new Date(Date.UTC(year, month - 1 + offset, 1))
  const last = new Date(Date.UTC(first.getUTCFullYear(), first.getUTCMonth() + 1, 0))
  const iso = (d: Date) => d.toISOString().slice(0, 10)
  return { start: iso(first), end: iso(last) }
}

export function buildDayCoverage(points: ChargeDataPoint[], year: number, month: number): DayCoverage[] {
  const daysInMonth = new Date(year, month, 0).getDate()
  const withData = new Set<number>()
  for (const p of points) {
    const d = new Date(p.timestamp)
    if (d.getFullYear() === year && d.getMonth() + 1 === month) withData.add(d.getDate())
  }
  let bestStart = 0, bestLen = 0, runStart = 0, runLen = 0
  for (let day = 1; day <= daysInMonth; day++) {
    if (withData.has(day)) { runLen = 0; continue }
    if (runLen === 0) runStart = day
    runLen++
    if (runLen > bestLen) { bestLen = runLen; bestStart = runStart }
  }
  return Array.from({ length: daysInMonth }, (_, i) => {
    const day = i + 1
    return {
      day,
      hasData: withData.has(day),
      inLongestGap: bestLen >= 2 && day >= bestStart && day < bestStart + bestLen,
    }
  })
}
