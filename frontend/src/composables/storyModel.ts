/**
 * Reine Ableitung des Story-Dashboards aus vorhandenen Statistiken.
 *
 * Keine Vue-Abhaengigkeit, keine Formatierung, keine i18n: hier entstehen nur Zahlen,
 * Vorzeichen und Reihenfolge. Saetze und Einheiten baut die Komponente aus i18n-Keys.
 */
import type { StatisticsData, ChargeDataPoint } from './useDashboardStats'
import { VERBRENNER_G_PER_KM, STROMMIX_G_PER_KWH, OEKO_G_PER_KWH } from '../components/dashboard/co2'
import type { EnergyFlow, RangeEstimate } from './storyEnergy'
import type { ModelPeers, RankResult, DataQuality, TemperatureEffect, TripMonth } from './storyInsights'
import type { Moment } from './storyMoments'

export type Sentiment = 'good' | 'warn' | 'bad' | 'neutral'
/** Feste Reihenfolge der Kacheln, siehe TILE_ORDER. */
export type TileId = 'cost' | 'trips' | 'flow' | 'range' | 'wltp' | 'peer' | 'battery'
export type SparkVariant = 'line' | 'bars' | 'range'

export interface StoryTile {
  id: TileId
  value: number
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
  /** Nur 'cost': Ladungen des Monats ohne Preis. Die Kosten sind dann zu niedrig, die Kachel nennt die Zahl statt eines Deltas. */
  pricelessCharges?: number
  /** Nur 'cost': dem Vormonat fehlen Preise, ein Vergleich wäre schief. */
  previousIncomplete?: boolean
  /** Nur 'cost', laufender Monat unter der Strommenge des Vormonats: was diese Menge jetzt kostet. */
  projection?: { eur: number; previousEur: number }
  /** Nur 'battery': berechneter Wert, keine eigene Messung. */
  estimated?: boolean
  /** Nur 'trips': Fahrten des Monats. */
  tripMonth?: TripMonth
}

export type FeedId =
  | 'consumption_trend' | 'home_savings' | 'most_expensive' | 'peer_position' | 'phantom_locked' | 'cost_trend' | 'distance_trend'
  | 'model_peers' | 'community_rank' | 'data_quality' | 'temperature_effect' | 'temperature_locked' | 'moment'
  | 'drive_vs_charge'

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
  /** Nur 'moment': ein Moment, der nicht Hero wurde, als Feed-Satz. */
  moment?: Moment
}

export interface StoryInput {
  month: StatisticsData
  previousMonth: StatisticsData | null
  /** Sechs Monatswerte, aeltester zuerst, letzter ist der gewaehlte Monat. */
  monthlyCosts: number[]
  monthlyConsumption: number[]
  wltpConsumption: number | null
  sohPercent: number | null
  /** Akkuwert berechnet statt selbst gemessen: die Kachel zeigt ihn mit Tilde und "geschätzt". */
  sohEstimated?: boolean
  odometerKm: number | null
  savingsEur: number | null
  amortisationYearsRemaining: number | null
  energySplitLocked: boolean
  flow: EnergyFlow | null
  range: RangeEstimate | null
  /** Du gegen alle Fahrer deines Modells (alle Varianten, Lifetime); null ohne Vergleich. */
  modelPeers: ModelPeers | null
  rank: RankResult | null
  quality: DataQuality | null
  temperature: TemperatureEffect | null
  /** True, wenn Fahrten-Analytik gesperrt ist und kein Temperatur-Effekt vorliegt. */
  temperatureLocked: boolean
  /** Ladungen ohne Preis im Vormonat (die des Monats stehen in quality). */
  pricelessPrevious: number
  /** Der gewählte Monat läuft noch: mit dem Vormonat erst ab dessen Strommenge vergleichen. */
  runningMonth?: boolean
  /** Fahrten des Monats; null ohne Fahrten oder ohne Berechtigung. */
  tripMonth?: TripMonth | null
}

export interface DayCoverage {
  day: number
  hasData: boolean
  inLongestGap: boolean
  /** Nach heute: noch nicht gewesen, also weder Lücke noch fehlend. */
  future: boolean
  /** Ladungen und geladene kWh an diesem Tag, für die Tagesanzeige. */
  charges: number
  kwh: number
  /** Fahrten, die an diesem Tag begannen, und ihre km. */
  trips: number
  tripKm: number
}

/** Was die Tagesleiste von einer Fahrt braucht. */
export interface DayTrip {
  tripStartedAt?: string | Date | null
  distanceKm?: number | null
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
/** Ab 10 % mehr oder weniger Strecke als im Vormonat ist das eine Nachricht. */
const MIN_DISTANCE_DIFF_SHARE = 0.1
const MIN_CONSUMPTION_DIFF = 0.2
/** Ladeverlust: bis 12 Prozent ueblich (AC-Pauschale 10), bis 20 auffaellig, darueber Problem. */
const FLOW_LOSS_GOOD_PERCENT = 12
const FLOW_LOSS_WARN_PERCENT = 20
/** Darunter fährst du so günstig wie die anderen Fahrer deines Modells. */
const MODEL_PEERS_SAME_PERCENT = 5
const FEED_MAX = 5
/** Ab diesem Verhältnis Verbrauch aus Ladungen zu Verbrauch beim Fahren lohnt der Abgleich als Satz. */
const DRIVE_VS_CHARGE_RATIO = 1.2
/** Vergleiche gegen andere Fahrer erst ab so vielen Fahrern, darunter entfallen Satz und Kachel. */
export const MIN_PEERS = 5

export function petrolCostEur(distanceKm: number | null, eurPerLitre: number): number | null {
  if (distanceKm == null || distanceKm <= 0) return null
  return (distanceKm / 100) * PETROL_L_PER_100KM * eurPerLitre
}

/** Der Hero-Satz des Monats: km für Euro, sonst Euro für Ladungen, sonst was da ist. Kosten 0 heißt ohne Preise. */
export interface HeroLine {
  kind: 'km_cost' | 'cost_charges' | 'km_charges' | 'charges'
  km: number | null
  eur: number | null
  charges: number
}

export function heroLine(input: { distanceKm: number | null; energyCostEur: number | null; charges: number }): HeroLine {
  const km = input.distanceKm != null && input.distanceKm > 0 ? input.distanceKm : null
  const eur = input.energyCostEur != null && input.energyCostEur > 0 ? input.energyCostEur : null
  const kind = km != null ? (eur != null ? 'km_cost' : 'km_charges') : (eur != null ? 'cost_charges' : 'charges')
  return { kind, km, eur, charges: input.charges }
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

function pricelessInMonth(input: StoryInput): number {
  return input.quality?.priceless ?? 0
}

/** Stromkosten zweier Monate sind nur vergleichbar, wenn keinem Ladungen ohne Preis fehlen. */
function costsComparable(input: StoryInput): boolean {
  return pricelessInMonth(input) === 0 && input.pricelessPrevious === 0
}

/**
 * Stromkosten gegen den Vormonat. Ein laufender Monat ist erst ab der Strommenge des Vormonats
 * vergleichbar, vorher hiesse "weniger Euro" nur "weniger Tage". Bis dahin die Prognose: die
 * Menge des Vormonats zum Preis je kWh dieses Monats. Abgeschlossene Monate vergleichen immer.
 */
function costComparison(input: StoryInput): { delta: number } | { projection: { eur: number; previousEur: number } } | null {
  const m = input.month
  const prev = input.previousMonth
  if (!prev || !costsComparable(input)) return null
  if (!input.runningMonth || m.totalKwhCharged >= prev.totalKwhCharged) return { delta: m.energyCostEur - prev.energyCostEur }
  if (!(m.totalKwhCharged > 0) || !(prev.totalKwhCharged > 0)) return null
  return { projection: { eur: (m.energyCostEur / m.totalKwhCharged) * prev.totalKwhCharged, previousEur: prev.energyCostEur } }
}

export function buildStoryTiles(input: StoryInput): StoryTile[] {
  const m = input.month
  const tiles: StoryTile[] = []

  // Stromkosten, nicht Gesamtkosten: Fixkosten fallen zeitanteilig oder einmalig an und wuerden
  // den Monatsvergleich dominieren (eine Reifenrechnung ist kein Fahrverhalten). Fixkosten stehen
  // in den Hero-Schichten und im Kosten-Sheet.
  // Ladungen ohne Preis zaehlen in den Stromkosten als 0 €: ein Monat mit solchen Ladungen ist zu billig,
  // ein Vergleich mit ihm waere schief. Dann kein Delta, die Kachel nennt die preislosen Ladungen.
  const priceless = pricelessInMonth(input)
  const comparison = costComparison(input)
  const costDelta = comparison && 'delta' in comparison ? comparison.delta : null
  tiles.push({
    id: 'cost',
    value: m.energyCostEur,
    delta: costDelta,
    deltaPercent: null,
    sentiment: priceless > 0 ? 'warn'
      : costDelta == null || Math.abs(costDelta) < MIN_COST_DIFF_EUR ? 'neutral' : costDelta <= 0 ? 'good' : 'warn',
    spark: input.monthlyCosts,
    sparkVariant: 'line',
    ...(priceless > 0 ? { pricelessCharges: priceless } : {}),
    ...(input.previousMonth && input.pricelessPrevious > 0 ? { previousIncomplete: true } : {}),
    ...(comparison && 'projection' in comparison ? { projection: comparison.projection } : {}),
  })

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

  const tm = input.tripMonth
  if (tm && tm.driveKwhPer100km != null) {
    tiles.push({
      id: 'trips',
      value: tm.driveKwhPer100km,
      delta: null,
      deltaPercent: null,
      sentiment: 'neutral',
      spark: [],
      sparkVariant: 'bars',
      tripMonth: tm,
    })
  }

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

  const pb = m.peerBenchmark
  if (pb && pb.userPeriodConsumptionKwhPer100km != null && pb.peerAvgConsumptionKwhPer100km != null && pb.uniquePeerUsers >= MIN_PEERS) {
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
      estimated: input.sohEstimated ?? false,
    })
  }

  const thin = m.totalCharges > 0 && m.totalCharges < MIN_SOLID_CHARGES ? m.totalCharges : undefined
  if (thin != null) {
    for (const t of tiles) if (t.id === 'cost' || t.id === 'wltp' || t.id === 'peer') t.thinCharges = thin
  }
  return tiles
}

/**
 * extra: weitere Eintraege (Momente), die um die fuenf Plaetze mitkonkurrieren.
 * omit: Eintraege, die schon der Hero sagt.
 */
/** max: Plätze statt FEED_MAX, nur für die lokale Testansicht aller Sätze (?feed=alle). */
export function buildStoryFeed(input: StoryInput, opts: { extra?: StoryFeedItem[]; omit?: FeedId[]; max?: number } = {}): StoryFeedItem[] {
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

  // Strecke ohne Hochrechnung: km kommen bei Hand-Erfassern in Spruengen (km-Stand nur beim Laden).
  // "Mehr" steht fest, sobald es erreicht ist; "weniger" erst nach Monatsende, vorher waeren es nur weniger Tage.
  const km = m.totalDistanceKm
  const prevKm = prev?.totalDistanceKm
  if (km != null && km > 0 && prevKm != null && prevKm > 0) {
    const diff = km - prevKm
    const share = diff / prevKm
    if (Math.abs(share) >= MIN_DISTANCE_DIFF_SHARE && (diff > 0 || !input.runningMonth)) {
      items.push({
        id: 'distance_trend',
        question: 1,
        relevance: Math.min(60, 25 + Math.abs(share) * 50),
        sentiment: 'neutral',
        params: { diff, current: km, previous: prevKm },
      })
    }
  }

  const comparison = costComparison(input)
  if (prev && prev.energyCostEur > 0 && comparison && 'delta' in comparison && Math.abs(comparison.delta) >= MIN_COST_DIFF_EUR) {
    const diff = comparison.delta
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
  if (pb?.userPeriodCostPerKwh != null && pb.peerAvgCostPerKwh != null && pb.uniquePeerUsers >= MIN_PEERS) {
    const diff = pb.userPeriodCostPerKwh - pb.peerAvgCostPerKwh
    items.push({
      id: 'peer_position',
      question: 3,
      relevance: 45 + Math.min(40, Math.abs(diff) * 400),
      sentiment: diff <= 0 ? 'good' : 'warn',
      params: { diffCt: diff * 100, peers: pb.uniquePeerUsers },
    })
  }

  // Alle Varianten des Modells zusammen: der Unterschied kommt meist vom Ladepreis, nicht von der Variante.
  const mp = input.modelPeers
  if (mp && mp.cars >= MIN_PEERS) {
    const same = Math.abs(mp.deltaPercent) < MODEL_PEERS_SAME_PERCENT
    items.push({
      id: 'model_peers',
      question: 3,
      relevance: same ? 45 : 40 + Math.min(35, Math.abs(mp.deltaPercent)),
      // Nie Amber: teurer als andere ist hier eine Auskunft, kein Fehler.
      sentiment: !same && mp.deltaPercent > 0 ? 'good' : 'neutral',
      params: {
        deltaPercent: mp.deltaPercent, cars: mp.cars, same: same ? 1 : 0, reasonPrice: mp.reason === 'price' ? 1 : 0,
        theirsPrice: mp.theirs.costPerKwh, minePrice: mp.mine.costPerKwh, theirsCons: mp.theirs.consumption, mineCons: mp.mine.consumption,
      },
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

  // Ladungen zeigen alles, was der Akku verliert; das Fahrzeug misst nur den Antrieb. Großer Abstand: Satz.
  const drive = input.tripMonth?.driveKwhPer100km
  const charged = m.avgConsumptionKwhPer100km
  if (drive != null && drive > 0 && charged != null && charged / drive >= DRIVE_VS_CHARGE_RATIO) {
    items.push({
      id: 'drive_vs_charge',
      question: 2,
      relevance: 55 + Math.min(30, (charged / drive - 1) * 50),
      sentiment: 'neutral',
      params: { drive, charged },
    })
  }

  items.push(...(opts.extra ?? []))
  const omit = new Set(opts.omit ?? [])
  const ranked = items.filter(i => !omit.has(i.id)).sort((a, b) => b.relevance - a.relevance)
  // Hoechstens ein gesperrter Eintrag, immer zuletzt: ein Upsell verdraengt nie echte Erkenntnisse.
  const locked: StoryFeedItem | null = input.energySplitLocked
    ? { id: 'phantom_locked', question: 4, relevance: 0, sentiment: 'neutral', locked: true, params: {} }
    : input.temperatureLocked && !temp
      ? { id: 'temperature_locked', question: 2, relevance: 0, sentiment: 'neutral', locked: true, params: {} }
      : null
  const max = opts.max ?? FEED_MAX
  const slots = locked ? max - 1 : max
  const shown = withOnePositive(ranked, slots)
  return locked ? [...shown, locked] : shown
}

/** Die ersten n nach Relevanz; fehlt darin ein positiver Satz, ersetzt der beste positive den letzten Platz. */
function withOnePositive(ranked: StoryFeedItem[], n: number): StoryFeedItem[] {
  const top = ranked.slice(0, n)
  if (top.some(i => i.sentiment === 'good')) return top
  const good = ranked.slice(n).find(i => i.sentiment === 'good')
  return good && top.length === n ? [...top.slice(0, n - 1), good] : top
}

export function monthWindow(year: number, month: number, offset = 0): { start: string; end: string } {
  const first = new Date(Date.UTC(year, month - 1 + offset, 1))
  const last = new Date(Date.UTC(first.getUTCFullYear(), first.getUTCMonth() + 1, 0))
  const iso = (d: Date) => d.toISOString().slice(0, 10)
  return { start: iso(first), end: iso(last) }
}

const MIN_GAP_DAYS = 3
const LONG_GAP_DAYS = 7

/**
 * Die Lücke, die die Qualitätszeile nennt: die längste ab 3 Tagen, im laufenden Monat erst über
 * 7 Tage (vorher ist es nur eine Pause). Tage nach heute sind nie Lücke.
 */
export function visibleGap(days: DayCoverage[]): { from: number; to: number } | null {
  const g = days.filter(d => d.inLongestGap && !d.future)
  if (g.length < MIN_GAP_DAYS) return null
  const monthOver = days.length > 0 && !days.some(d => d.future)
  if (!monthOver && g.length <= LONG_GAP_DAYS) return null
  return { from: g[0].day, to: g[g.length - 1].day }
}

/** Tage des Monats: Ladungen und Fahrten je Tag, Tage nach heute, längste Strecke ohne beides. */
export function buildDayCoverage(points: ChargeDataPoint[], year: number, month: number, now: number = Date.now(), trips: DayTrip[] = []): DayCoverage[] {
  const daysInMonth = new Date(year, month, 0).getDate()
  const today = new Date(now)
  const lastPast = today.getFullYear() === year && today.getMonth() + 1 === month ? today.getDate() : daysInMonth
  const withData = new Set<number>()
  const perDay = new Map<number, { charges: number; kwh: number }>()
  for (const p of points) {
    const d = new Date(p.timestamp)
    if (d.getFullYear() !== year || d.getMonth() + 1 !== month) continue
    withData.add(d.getDate())
    const acc = perDay.get(d.getDate()) ?? { charges: 0, kwh: 0 }
    acc.charges += p.charges ?? 1
    acc.kwh += p.kwhCharged ?? 0
    perDay.set(d.getDate(), acc)
  }
  const tripDay = new Map<number, { trips: number; km: number }>()
  for (const tr of trips) {
    if (!tr.tripStartedAt) continue
    const d = new Date(tr.tripStartedAt)
    if (d.getFullYear() !== year || d.getMonth() + 1 !== month) continue
    withData.add(d.getDate())
    const acc = tripDay.get(d.getDate()) ?? { trips: 0, km: 0 }
    acc.trips += 1
    acc.km += tr.distanceKm ?? 0
    tripDay.set(d.getDate(), acc)
  }
  let bestStart = 0, bestLen = 0, runStart = 0, runLen = 0
  for (let day = 1; day <= lastPast; day++) {
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
      future: day > lastPast,
      charges: perDay.get(day)?.charges ?? 0,
      kwh: perDay.get(day)?.kwh ?? 0,
      trips: tripDay.get(day)?.trips ?? 0,
      tripKm: tripDay.get(day)?.km ?? 0,
    }
  })
}
