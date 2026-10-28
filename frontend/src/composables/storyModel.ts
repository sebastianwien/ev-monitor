/**
 * Reine Ableitung des Story-Dashboards aus vorhandenen Statistiken.
 *
 * Keine Vue-Abhaengigkeit, keine Formatierung, keine i18n: hier entstehen nur Zahlen,
 * Vorzeichen und Reihenfolge. Saetze und Einheiten baut die Komponente aus i18n-Keys.
 */
import type { StatisticsData, ChargeDataPoint, ChargingTypeSplit } from './useDashboardStats'
import { PETROL_KG_CO2_PER_L, STROMMIX_G_PER_KWH, OEKO_G_PER_KWH } from '../components/dashboard/co2'
import { combustionLiters } from '../utils/costMix'
import { energyUse, type EnergyFlow, type EnergyUse, type RangeEstimate } from './storyEnergy'
import type { ModelPeers, DataQuality, TemperatureEffect, TripMonth } from './storyInsights'

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
  /** Nur 'co2': Ausstoß je km im Vergleich, Wert der Kachel sind die gesparten kg. */
  co2?: {
    petrolGPerKm: number; gridGPerKm: number; greenGPerKm: number
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
  | 'autumn_forecast' | 'peer_cost_eur' | 'home_savings' | 'most_expensive' | 'cost_trend' | 'distance_trend'
  | 'data_quality' | 'temperature_effect'

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

export interface WeatherOutlook {
  deltaC: number
  aheadDays: number
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
  /** Strecke und Verbrauch je Tag seit der ersten Ladung, für CO₂ gesamt. */
  lifetimeSeries: Co2Point[]
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
const EXPENSIVE_FACTOR = 1.6
/** Unter diesen Schwellen gibt es keinen Feed-Satz: "0 € weniger" ist keine Erkenntnis. */
/** Unter drei Ladungen im Monat zeigt die Kachel, worauf der Wert beruht. Reichweite ist davon frei, sie rechnet mit 4 Wochen. */
export const MIN_SOLID_CHARGES = 3
const MIN_COST_DIFF_EUR = 1
/** Ab 10 % mehr oder weniger Strecke als im Vormonat ist das eine Nachricht. */
const MIN_DISTANCE_DIFF_SHARE = 0.1
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

export function co2Comparison(distanceKm: number, consumptionKwhPer100km: number, petrolLitersPer100km: number) {
  const kwh = (distanceKm / 100) * consumptionKwhPer100km
  return {
    petrolKg: (distanceKm / 100) * petrolLitersPer100km * PETROL_KG_CO2_PER_L,
    gridKg: (kwh * STROMMIX_G_PER_KWH) / 1000,
    greenKg: (kwh * OEKO_G_PER_KWH) / 1000,
  }
}

/** Ein Tag der Lifetime-Reihe, soweit CO₂ ihn braucht. */
export interface Co2Point {
  timestamp: string
  distanceKm: number | null
  consumptionKwhPer100km: number | null
}

/** CO₂ weniger an einem Tag mit Strommix; Tage ohne Verbrauch zählen nicht, mehr als ein Benziner nie negativ. */
export function co2SavedKg(p: Co2Point, petrolLitersPer100km: number): number {
  const km = p.distanceKm ?? 0
  if (km <= 0 || p.consumptionKwhPer100km == null) return 0
  const c = co2Comparison(km, p.consumptionKwhPer100km, petrolLitersPer100km)
  return Math.max(0, c.petrolKg - c.gridKg)
}

/** CO₂ weniger seit der ersten Ladung, Tag für Tag summiert wie beim Meilenstein. */
export function lifetimeCo2(series: Co2Point[], petrolLitersPer100km: number): { kg: number; since: number } | null {
  const times = series.map(p => Date.parse(p.timestamp)).filter(t => !Number.isNaN(t))
  if (times.length === 0) return null
  const kg = series.reduce((sum, p) => sum + co2SavedKg(p, petrolLitersPer100km), 0)
  return { kg, since: Math.min(...times) }
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

  // CO₂ gegen einen Benziner auf derselben Strecke, mit deutschem Strommix gerechnet
  if (hasDistance) {
    const liters = petrolLitersPer100km(input.vehicleCategory)
    const c = co2Comparison(m.totalDistanceKm!, m.avgConsumptionKwhPer100km!, liters)
    const lifetime = lifetimeCo2(input.lifetimeSeries, liters)
    tiles.push({
      id: 'co2',
      value: c.petrolKg - c.gridKg,
      delta: null,
      deltaPercent: null,
      sentiment: c.petrolKg > c.gridKg ? 'good' : 'neutral',
      spark: [],
      sparkVariant: 'bars',
      co2: {
        petrolGPerKm: liters * PETROL_KG_CO2_PER_L * 10,
        gridGPerKm: (m.avgConsumptionKwhPer100km! / 100) * STROMMIX_G_PER_KWH,
        greenGPerKm: (m.avgConsumptionKwhPer100km! / 100) * OEKO_G_PER_KWH,
        petrolLiters: (m.totalDistanceKm! / 100) * liters,
        petrolLitersPer100km: liters,
        category: input.vehicleCategory,
        // Gesamt gleich Monat sagt nichts Neues (erster Monat)
        lifetime: lifetime && lifetime.kg > c.petrolKg - c.gridKg + 1 ? lifetime : null,
      },
    })
  }

  const thin = m.totalCharges > 0 && m.totalCharges < MIN_SOLID_CHARGES ? m.totalCharges : undefined
  if (thin != null) {
    for (const t of tiles) if (t.id === 'cost' || t.id === 'wltp' || t.id === 'peer') t.thinCharges = thin
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

  // Preisvergleich in Euro: was die Fahrer des Modells (alle Varianten) fuer dieselbe Strommenge
  // gezahlt haetten, mit ihrem Preis je kWh. Nur ohne Ladungen ohne Preis, sonst stimmt die Summe nicht.
  const mp = input.modelPeers
  if (mp && mp.cars >= MIN_PEERS && m.totalKwhCharged > 0 && m.energyCostEur > 0 && m.totalCharges >= MIN_SOLID_CHARGES
      && input.elapsedDays >= MIN_COMPARE_DAYS && (input.quality?.priceless ?? 0) === 0) {
    const theirsEur = m.totalKwhCharged * mp.theirs.costPerKwh
    const diff = m.energyCostEur - theirsEur
    if (Math.abs(diff) >= MIN_COST_DIFF_EUR) {
      items.push({
        id: 'peer_cost_eur',
        question: 3,
        // Zeilen mit Vergleichsbasis stehen vor den einfachen Saetzen (teuerste Ladung, Trends).
        relevance: 60 + Math.min(30, Math.abs(diff)),
        // Teurer als andere ist eine Auskunft fuer die Planung, kein Fehler: nie Amber.
        sentiment: diff < 0 ? 'good' : 'neutral',
        params: { diffEur: diff, mineEur: m.energyCostEur, theirsEur, cars: mp.cars },
      })
    }
  }

  // Herbst-Prognose: Strecke der letzten Tage auf 30 Tage, mal Mehrverbrauch im Winter, mal eigener Preis.
  // Verbrauch aus der Statistik (Sommer und Winter ueber alle Jahre, Winter notfalls von den Modellfahrern).
  const r = input.range
  const winter = r?.winter ?? r?.peerWinter ?? null
  const rec = input.recent
  if (input.runningMonth && AUTUMN_MONTHS.includes(input.monthOfYear) && r?.summer && winter && rec?.distanceKm != null && rec.distanceKm > 0 && rec.days > 0
      && m.avgCostPerKwh > 0 && m.energyCostEur > 0 && input.elapsedDays >= MIN_COMPARE_DAYS && (input.quality?.priceless ?? 0) === 0) {
    const km30 = rec.distanceKm / rec.days * 30
    const deltaEur = km30 * (winter.consumption - r.summer.consumption) / 100 * m.avgCostPerKwh
    if (deltaEur >= MIN_FORECAST_EUR) {
      items.push({
        id: 'autumn_forecast',
        question: 1,
        relevance: 75,
        sentiment: 'neutral',
        params: {
          deltaEur, currentEur: m.energyCostEur, nextEur: m.energyCostEur + deltaEur, nextMonth: FIRST_WINTER_MONTH, peerWinter: r.winter ? 0 : 1,
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

  const omit = new Set(opts.omit ?? [])
  const ranked = items.filter(i => !omit.has(i.id)).sort((a, b) => b.relevance - a.relevance)
  return withOnePositive(ranked, opts.max ?? FEED_MAX)
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
