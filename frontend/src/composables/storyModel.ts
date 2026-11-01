/**
 * Reine Ableitung des Story-Dashboards aus vorhandenen Statistiken.
 *
 * Keine Vue-Abhaengigkeit, keine Formatierung, keine i18n: hier entstehen nur Zahlen,
 * Vorzeichen und Reihenfolge. Saetze und Einheiten baut die Komponente aus i18n-Keys.
 */
import type { StatisticsData, ChargeDataPoint, ChargingTypeSplit, Co2Statistics } from './useDashboardStats'
import { combustionLiters } from '../utils/costMix'
import { energyUse, type EnergyFlow, type EnergyUse, type RangeEstimate } from './storyEnergy'
import type { ModelPeers, DataQuality, TemperatureEffect, TripMonth, StandbyChange } from './storyInsights'
import type { OperatorGap } from './storyPlaces'
import { dayKeyOf, drivenByDay, type DrivenDay, type OdometerGap } from './storyDay'

export type Sentiment = 'good' | 'warn' | 'bad' | 'neutral'
/** Feste Reihenfolge der Kacheln, siehe TILE_ORDER. */
export type TileId = 'cost' | 'trips' | 'flow' | 'charging' | 'range' | 'wltp' | 'peer' | 'battery' | 'co2'
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
  /** Fahren, Stand und Laden des Monats (nur id 'flow', nur mit Energiedaten der Fahrten). */
  use?: EnergyUse
  /** Rohdaten fuer die Akku-Silhouette (nur id 'range'). */
  range?: RangeEstimate
  /** Nur 'wltp' und 'peer': Fahrverbrauch aus den Fahrten oder Verbrauch aus Ladungen (mit Strom im Stand). */
  basis?: 'drive' | 'charges'
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
  /** Nur 'trips' und 'flow' (Standzeiten fürs Sheet): Fahrten des Monats. */
  tripMonth?: TripMonth
  /** Nur 'co2': Ausstoß je km im Vergleich (aus der Bilanz der API), Wert der Kachel sind die gesparten kg. */
  co2?: {
    petrolGPerKm: number; gridGPerKm: number
    /** Liter, die ein Benziner der Fahrzeugklasse auf der Monatsstrecke getankt hätte. */
    petrolLiters: number
    petrolLitersPer100km: number
    /** Fahrzeugklasse (VehicleCategory), null bei unbekannter Klasse. */
    category: string | null
    /** CO₂ weniger seit der ersten Ladung; null, wenn das nicht mehr als der Monat ist. */
    lifetime: { kg: number; since: number } | null
  }
  /** Nur 'charging': kWh und Preis je kWh nach Ladeart, Wert der Kachel ist der Anteil Schnellladen. */
  charging?: ChargingTypeSplit
}

export type FeedId =
  | 'autumn_forecast' | 'peer_cost_eur' | 'home_savings' | 'cpo_price' | 'cost_trend' | 'data_quality' | 'temperature_effect'
  | 'standby_change'

export interface StoryFeedItem {
  id: FeedId
  /** Nummer der Fahrerfrage (1 Kosten, 2 Realitaet, 3 Community, 4 Gesundheit, 5 Oeko). */
  question: 1 | 2 | 3 | 4 | 5
  relevance: number
  sentiment: Sentiment
  locked?: boolean
  /** Rohwerte fuer den Satz, die Komponente formatiert sie. */
  params: Record<string, number>
  /** Ladung, die die Zeile oeffnet (einzige Ladung ohne Preis) */
  logId?: string | null
  /** Nur Kostentrend: Kosten und Strecke je Monat für die Balken, aeltester zuerst. */
  months?: FeedMonth[]
  /** Nur teurer Betreiber: Betreiber, Preise, Karte und Vergleich fürs Preis-Sheet. */
  operator?: OperatorGap
  /** Textschluessel ohne Zahl, z. B. die Leaderboard-Kategorie. */
  label?: string
}

export interface FeedMonth {
  /** Kalendermonat 1 bis 12. */
  month: number
  eur: number
  km: number
}

export interface WeatherOutlook {
  deltaC: number
  aheadDays: number
}

export interface StoryInput {
  month: StatisticsData
  previousMonth: StatisticsData | null
  /** Sechs Monatswerte, aeltester zuerst, letzter ist der gewaehlte Monat. */
  monthlyCosts: number[]
  /** Strecke je Monat, gleiche Reihenfolge wie monthlyCosts. */
  monthlyDistance?: number[]
  /** Betreiber des Monats, bei dem du am meisten mehr zahlst als andere Fahrer dort; null ohne. */
  operatorGap?: OperatorGap | null
  /** Standverlust je Tag deutlich über den Vormonaten; null ohne Auffälligkeit oder ohne Freischaltung. */
  standbyChange?: StandbyChange | null
  monthlyConsumption: number[]
  /** Fahrverbrauch je Monat, gleiche Reihenfolge, 0 ohne Wert; Balken der WLTP-Kachel, wenn sie mit Fahrten rechnet. */
  monthlyDriveConsumption?: number[]
  /** Saisonverbrauch aus Ladungen (Euro-Fragen wie die Herbst-Prognose), Winter notfalls von den Modellfahrern. */
  chargeSeasons?: { summer: number | null; winter: number | null; peerWinter: number | null } | null
  wltpConsumption: number | null
  sohPercent: number | null
  /** Akkuwert berechnet statt selbst gemessen: die Kachel zeigt ihn mit Tilde und "geschätzt". */
  sohEstimated?: boolean
  odometerKm: number | null
  savingsEur: number | null
  amortisationYearsRemaining: number | null
  flow: EnergyFlow | null
  range: RangeEstimate | null
  /** Du gegen alle Fahrer deines Modells (alle Varianten, Lifetime); null ohne Vergleich. */
  modelPeers: ModelPeers | null
  quality: DataQuality | null
  temperature: TemperatureEffect | null
  /** Ladungen ohne Preis im Vormonat (die des Monats stehen in quality). */
  pricelessPrevious: number
  /** Kalendermonat des gewaehlten Monats (1 bis 12), fuer die Herbst-Prognose. */
  monthOfYear: number
  /** Tage des Monats, die zaehlen: im laufenden Monat der heutige Tag, sonst die Monatslaenge. */
  elapsedDays: number
  /** Strecke der letzten Tage (eigene Statistik je Auto), Basis der Herbst-Prognose. */
  recent: { distanceKm: number | null; days: number } | null
  /** Wetter am Heimladeort: Mittel der kommenden Tage gegen die letzte Woche, Grad Celsius. */
  weather?: WeatherOutlook | null
  /** Fahrzeugklasse des Autos (VehicleCategory), bestimmt den Benziner im Vergleich. */
  vehicleCategory: string | null
  /** CO₂ weniger seit der ersten Ladung (lifetimeCo2), null ohne Bilanz. */
  lifetimeCo2: LifetimeCo2 | null
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

/** Benziner der Fahrzeugklasse (wie Modellliste und Monatsrückblick), ohne Klasse 7,5 l/100 km. */
export function petrolLitersPer100km(category: string | null | undefined): number {
  return combustionLiters({ litersPer100km: null, fuel: 'petrol' }, category)
}
/** Rueckfall-Literpreis Super (E5/E10-Mittel, Tankerkoenig-Bundesschnitt 02.10.2026), wenn die Preis-API nicht antwortet. */
export const PETROL_EUR_PER_L_DE = 2.13

const WLTP_WARN_PERCENT = 5
const PEER_NEUTRAL_PERCENT = 1
/** Unter diesen Schwellen gibt es keinen Feed-Satz: "0 € weniger" ist keine Erkenntnis. */
/** Unter drei Ladungen im Monat zeigt die Kachel, worauf der Wert beruht. Reichweite ist davon frei, sie rechnet mit 4 Wochen. */
export const MIN_SOLID_CHARGES = 3
const MIN_COST_DIFF_EUR = 1
/** Preisvergleich in Euro und Herbst-Prognose erst, wenn der Monat so viele Tage zaehlt. */
const MIN_COMPARE_DAYS = 14
/** Herbst-Prognose: nur im Oktober, fuer den ersten Wintermonat. */
const AUTUMN_MONTHS = [10]
const FIRST_WINTER_MONTH = 11
/** Unter so vielen Euro Mehrkosten gibt es keine Prognose. */
const MIN_FORECAST_EUR = 5
/** Ab so vielen Grad kaelter nennt die Prognose den Wetter-Ausblick. */
const MIN_COLDER_C = 2
/** Ladeverlust: bis 12 Prozent ueblich (AC-Pauschale 10), bis 20 auffaellig, darueber Problem. */
const FLOW_LOSS_GOOD_PERCENT = 12
const FLOW_LOSS_WARN_PERCENT = 20
/** Hoechstens drei Zeilen unter "Diesen Monat": jede mit Vergleichsbasis im Satz. */
const FEED_MAX = 3
/** Feste Reihenfolge der Kacheln: Reichweite zuerst, Kosten zuletzt (der Hero nennt sie schon). */
const TILE_ORDER: TileId[] = ['range', 'charging', 'trips', 'co2', 'battery', 'flow', 'peer', 'wltp', 'cost']
/** Vergleiche gegen andere Fahrer erst ab so vielen Fahrern, darunter entfallen Satz und Kachel. */
export const MIN_PEERS = 5

/**
 * Preisvergleich in Euro: was die Fahrer des Modells (alle Varianten) für dieselbe Strommenge
 * gezahlt hätten, mit ihrem Preis je kWh. Nur mit genug Autos, Ladungen und Tagen und ohne
 * Ladungen ohne Preis, sonst stimmt die Summe nicht. Negativ heißt günstiger; null unter 1 €.
 * Gleiche Regel für "Diesen Monat" und den Monatsrückblick.
 */
export function peerCostDiffEur(
  m: { totalKwhCharged: number; energyCostEur: number; totalCharges: number },
  elapsedDays: number, priceless: number, mp: ModelPeers | null,
): number | null {
  if (!mp || mp.cars < MIN_PEERS || !(m.totalKwhCharged > 0) || !(m.energyCostEur > 0) || m.totalCharges < MIN_SOLID_CHARGES
      || elapsedDays < MIN_COMPARE_DAYS || priceless !== 0) return null
  const diff = m.energyCostEur - m.totalKwhCharged * mp.theirs.costPerKwh
  return Math.abs(diff) >= MIN_COST_DIFF_EUR ? diff : null
}

export function petrolCostEur(distanceKm: number | null, eurPerLitre: number, litersPer100km: number): number | null {
  if (distanceKm == null || distanceKm <= 0) return null
  return (distanceKm / 100) * litersPer100km * eurPerLitre
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

/** CO₂ weniger seit der ersten Ladung. */
export interface LifetimeCo2 { kg: number; since: number }

/** Lifetime-Bilanz der API mit dem Zeitpunkt der ersten Ladung der Reihe; null ohne Bilanz oder Punkt. */
export function lifetimeCo2(co2: Co2Statistics | null | undefined, series: { timestamp: string }[]): LifetimeCo2 | null {
  const times = series.map(p => Date.parse(p.timestamp)).filter(t => !Number.isNaN(t))
  if (co2 == null || times.length === 0) return null
  return { kg: co2.savedKg, since: Math.min(...times) }
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
  // WLTP misst nur das Fahren: mit Fahrten der Fahrverbrauch des Monats, sonst der Verbrauch aus Ladungen (mit Stand)
  const drive = m.driveConsumptionKwhPer100km ?? null
  const wltpValue = drive ?? (hasDistance ? m.avgConsumptionKwhPer100km : null)
  if (wltpValue != null && input.wltpConsumption != null && input.wltpConsumption > 0) {
    const d = pct(wltpValue, input.wltpConsumption)
    tiles.push({
      id: 'wltp',
      value: wltpValue,
      delta: null,
      deltaPercent: d,
      sentiment: d > WLTP_WARN_PERCENT ? 'warn' : 'good',
      spark: drive != null ? input.monthlyDriveConsumption ?? [] : input.monthlyConsumption,
      sparkVariant: 'line',
      reference: input.wltpConsumption,
      basis: drive != null ? 'drive' : 'charges',
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

  // Mit Energiedaten der Fahrten: Verwendung Fahren/Stand/Laden, Zahl ist der Verlust (Euro, ohne Preis kWh)
  const use = energyUse(m, tm ?? null)
  if (use) {
    tiles.push({
      id: 'flow',
      value: use.lossEur ?? (use.standbyKwh ?? 0) + use.chargeLossKwh,
      delta: null,
      deltaPercent: null,
      sentiment: 'neutral',
      spark: [],
      sparkVariant: 'range',
      flow: input.flow ?? undefined,
      use,
      // Größte Standzeiten für den Abschnitt "Stand" im Sheet
      ...(tm && tm.standbyGaps.length ? { tripMonth: tm } : {}),
    })
  } else if (input.flow) {
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

  const cs = m.chargingTypeSplit
  if (cs && cs.acKwh + cs.dcKwh > 0) {
    tiles.push({
      id: 'charging',
      value: Math.round((cs.dcKwh / (cs.acKwh + cs.dcKwh)) * 100),
      delta: null,
      deltaPercent: null,
      sentiment: 'neutral',
      spark: [cs.acKwh, cs.dcKwh],
      sparkVariant: 'range',
      charging: cs,
    })
  }

  if (input.range) {
    tiles.push({
      id: 'range',
      value: input.range.km,
      delta: null,
      deltaPercent: null,
      sentiment: 'neutral',
      spark: [input.range.summer?.km, input.range.km, input.range.winter?.km].filter((k): k is number => k != null),
      sparkVariant: 'bars',
      range: input.range,
    })
  }

  // Gleiche Größe auf beiden Seiten: Fahrverbrauch gegen Fahrverbrauch anderer Fahrer des Modells im selben
  // Zeitraum, wenn beide da sind; sonst Ladungen gegen Ladungen. Nie gemischt.
  const ownDrive = m.driveConsumptionKwhPer100km ?? null
  const modelDrive = m.modelDriveConsumptionKwhPer100km ?? null
  const pb = m.peerBenchmark
  if (ownDrive != null && modelDrive != null) {
    const d = pct(ownDrive, modelDrive)
    tiles.push({
      id: 'peer',
      value: ownDrive,
      delta: null,
      deltaPercent: d,
      sentiment: Math.abs(d) <= PEER_NEUTRAL_PERCENT ? 'neutral' : d < 0 ? 'good' : 'warn',
      spark: [modelDrive, ownDrive],
      sparkVariant: 'range',
      peerCount: m.modelDriveUsers ?? 0,
      basis: 'drive',
    })
  } else if (pb && pb.userPeriodConsumptionKwhPer100km != null && pb.peerAvgConsumptionKwhPer100km != null && pb.uniquePeerUsers >= MIN_PEERS) {
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
      basis: 'charges',
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

  // CO₂ gegen einen Benziner der Fahrzeugklasse, Bilanz aus der API (Strecke zwischen Tachoständen gegen Netzstrom)
  const co2 = m.co2
  if (co2 != null && co2.distanceKm > 0) {
    const lifetime = input.lifetimeCo2
    tiles.push({
      id: 'co2',
      value: co2.savedKg,
      delta: null,
      deltaPercent: null,
      sentiment: co2.savedKg > 0 ? 'good' : 'neutral',
      spark: [],
      sparkVariant: 'bars',
      co2: {
        petrolGPerKm: (co2.petrolKg / co2.distanceKm) * 1000,
        gridGPerKm: (co2.electricityKg / co2.distanceKm) * 1000,
        petrolLiters: co2.petrolLiters,
        petrolLitersPer100km: co2.petrolLitersPer100km,
        category: input.vehicleCategory,
        // Gesamt gleich Monat sagt nichts Neues (erster Monat)
        lifetime: lifetime && lifetime.kg > co2.savedKg + 1 ? lifetime : null,
      },
    })
  }

  const thin = m.totalCharges > 0 && m.totalCharges < MIN_SOLID_CHARGES ? m.totalCharges : undefined
  if (thin != null) {
    // Wenige Ladungen sagen nichts über einen Fahrverbrauch aus den Fahrten
    for (const t of tiles) if ((t.id === 'cost' || t.id === 'wltp' || t.id === 'peer') && t.basis !== 'drive') t.thinCharges = thin
  }
  return tiles.sort((a, b) => TILE_ORDER.indexOf(a.id) - TILE_ORDER.indexOf(b.id))
}

/**
 * extra: weitere Eintraege (Momente), die um die fuenf Plaetze mitkonkurrieren.
 * omit: Eintraege, die schon der Hero sagt.
 */
/** max: Plätze statt FEED_MAX, nur für die lokale Testansicht aller Sätze (?feed=alle). */
export function buildStoryFeed(input: StoryInput, opts: { omit?: FeedId[]; max?: number } = {}): StoryFeedItem[] {
  const m = input.month
  const items: StoryFeedItem[] = []

  const prev = input.previousMonth

  const comparison = costComparison(input)
  if (prev && prev.energyCostEur > 0 && comparison && 'delta' in comparison && Math.abs(comparison.delta) >= MIN_COST_DIFF_EUR) {
    const diff = comparison.delta
    items.push({
      id: 'cost_trend',
      question: 1,
      relevance: Math.min(90, 30 + Math.abs(diff)),
      sentiment: diff <= 0 ? 'good' : 'warn',
      params: { diff, current: m.energyCostEur, previous: prev.energyCostEur },
      months: feedMonths(input),
    })
  }

  if (input.savingsEur != null && input.savingsEur > 0) {
    items.push({
      id: 'home_savings',
      question: 1,
      // Die Ersparnis steht vorn: sie ist die Nachricht, für die man zu Hause lädt.
      relevance: 100,
      sentiment: 'good',
      params: {
        eur: input.savingsEur,
        monthsRemaining: input.amortisationYearsRemaining == null ? -1 : Math.round(input.amortisationYearsRemaining * 12),
      },
    })
  }

  // Preisvergleich in Euro: was die Fahrer des Modells (alle Varianten) fuer dieselbe Strommenge
  // gezahlt haetten, mit ihrem Preis je kWh. Nur ohne Ladungen ohne Preis, sonst stimmt die Summe nicht.
  const mp = input.modelPeers
  const diff = peerCostDiffEur(m, input.elapsedDays, input.quality?.priceless ?? 0, mp)
  if (mp && diff != null) {
    items.push({
      id: 'peer_cost_eur',
      question: 3,
      // Zeilen mit Vergleichsbasis stehen vor den einfachen Saetzen (teuerste Ladung, Trends).
      relevance: 60 + Math.min(30, Math.abs(diff)),
      // Teurer als andere ist eine Auskunft fuer die Planung, kein Fehler: nie Amber.
      sentiment: diff < 0 ? 'good' : 'neutral',
      params: { diffEur: diff, mineEur: m.energyCostEur, theirsEur: m.energyCostEur - diff, cars: mp.cars },
    })
  }

  // Teurer Betreiber: hier spart ein Kartenwechsel wirklich Geld. Nach der Ersparnis, vor Trends.
  const op = input.operatorGap
  if (op) {
    items.push({
      id: 'cpo_price',
      question: 1,
      relevance: 90 + Math.min(9, op.extraEur / 5),
      sentiment: 'neutral',
      params: { extraEur: op.extraEur },
      operator: op,
    })
  }

  // Mehr Standverlust als sonst: ein sachlicher Hinweis mit möglichen Ursachen, nie Alarm.
  const sb = input.standbyChange
  if (sb) {
    items.push({
      id: 'standby_change',
      question: 2,
      relevance: 70,
      sentiment: 'neutral',
      params: { perDay: sb.perDay, usualPerDay: sb.usualPerDay },
    })
  }

  // Herbst-Prognose: Strecke der letzten Tage auf 30 Tage, mal Mehrverbrauch im Winter, mal eigener Preis.
  // Es geht um Euro, darum Verbrauch aus Ladungen (Sommer und Winter ueber alle Jahre, Winter notfalls von den Modellfahrern).
  const seasons = input.chargeSeasons
  const winter = seasons?.winter ?? seasons?.peerWinter ?? null
  const summer = seasons?.summer ?? null
  const rec = input.recent
  if (input.runningMonth && AUTUMN_MONTHS.includes(input.monthOfYear) && summer && winter && rec?.distanceKm != null && rec.distanceKm > 0 && rec.days > 0
      && m.avgCostPerKwh > 0 && m.energyCostEur > 0 && input.elapsedDays >= MIN_COMPARE_DAYS && (input.quality?.priceless ?? 0) === 0) {
    const km30 = rec.distanceKm / rec.days * 30
    const deltaEur = km30 * (winter - summer) / 100 * m.avgCostPerKwh
    if (deltaEur >= MIN_FORECAST_EUR) {
      items.push({
        id: 'autumn_forecast',
        question: 1,
        relevance: 75,
        sentiment: 'neutral',
        params: {
          deltaEur, currentEur: m.energyCostEur, nextEur: m.energyCostEur + deltaEur, nextMonth: FIRST_WINTER_MONTH, peerWinter: seasons?.winter ? 0 : 1,
          ...(input.weather && input.weather.deltaC <= -MIN_COLDER_C ? { deltaC: Math.round(input.weather.deltaC) } : {}),
        },
      })
    }
  }

  const q = input.quality
  if (q && q.severity !== 'ok') {
    items.push({
      id: 'data_quality',
      question: 1,
      relevance: q.severity === 'bad' ? 80 : 35,
      sentiment: q.severity,
      params: { estimated: q.estimated, total: q.total, priceless: q.priceless, pricelessOpen: q.pricelessOpen },
      logId: q.pricelessOpenLogId ?? null,
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

  const omit = new Set(opts.omit ?? [])
  const ranked = items.filter(i => !omit.has(i.id)).sort((a, b) => b.relevance - a.relevance)
  return withOnePositive(ranked, opts.max ?? FEED_MAX)
}

/** Kosten und Strecke der sechs Monate; Monate ohne beides vor dem ersten Monat mit Daten fallen weg. */
function feedMonths(input: StoryInput): FeedMonth[] {
  const n = input.monthlyCosts.length
  const all = input.monthlyCosts.map((eur, i) => ({
    month: ((input.monthOfYear - (n - 1 - i) - 1) % 12 + 12) % 12 + 1,
    eur,
    km: input.monthlyDistance?.[i] ?? 0,
  }))
  const first = all.findIndex(x => x.eur > 0 || x.km > 0)
  return first < 0 ? [] : all.slice(first)
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



/** Fahrten und Strecke ohne Aufzeichnung eines Monats für die Tagesleiste. */
export interface MonthDrives {
  trips?: DayTrip[]
  gaps?: OdometerGap[]
}

/**
 * Tage des Monats: Ladungen und Fahrten je Tag, Tage nach heute, längste Strecke ohne beides. Strecke ohne
 * Aufzeichnung zählt in die km des Tages, an dem sie endet, nicht als Fahrt.
 */
export function buildDayCoverage(points: ChargeDataPoint[], year: number, month: number, now: number = Date.now(), drives: MonthDrives = {}): DayCoverage[] {
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
  const tripDay = new Map<number, DrivenDay>()
  const prefix = `${year}-${String(month).padStart(2, '0')}-`
  for (const [key, d] of drivenByDay(drives.trips ?? [], drives.gaps ?? [], dayKeyOf)) {
    if (!key.startsWith(prefix)) continue
    const day = Number(key.slice(prefix.length))
    withData.add(day)
    tripDay.set(day, d)
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
