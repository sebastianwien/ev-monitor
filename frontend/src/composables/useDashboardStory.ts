import { ref, computed, watch, type Ref } from 'vue'
import api from '../api/axios'
import chargingSavingsService from '../api/chargingSavingsService'
import { peerModelComparisonService, type PeerModelComparisonResponse } from '../api/peerModelComparisonService'
import { carService, type BatterySohEntry } from '../api/carService'
import { fuelPriceService, type FuelPrices } from '../api/fuelPriceService'
import { chargingBenchmarkService, type ChargingBenchmark } from '../api/chargingBenchmarkService'
import type { StatisticsData } from './useDashboardStats'
import {
  buildStoryTiles, buildStoryFeed, buildDayCoverage, monthWindow, PETROL_EUR_PER_L_DE, petrolLitersPer100km,
  type StoryTile, type StoryFeedItem, type DayCoverage,
} from './storyModel'
import { buildEnergyFlow, buildRange, costLayers, monthCosts, type MonthCosts } from './storyEnergy'
import {
  temperatureEffect, modelPeers, dataQuality, countPricelessInMonth, bestRank,
  type TripForTemperature, type RankEntry, buildLatest, sohIsEstimated, buildTripMonth, type StandbyPricing, type StoryLatest, type LatestLogSource, type LatestTripSource } from './storyInsights'
import {
  detectLive, detectJustCharged, detectLongTrip, detectFirstFrost, detectMonthChange, detectBattery, detectRank,
  detectSilence, detectGap, detectMilestone, detectExpensive, expensiveCandidate, pickMoment, momentFeedItems, DAY,
  type Moment, type MomentLog, type MomentTrip, type MomentPoint,
} from './storyMoments'
import { useMomentSeen } from './useMomentSeen'
import { benchmarkPairs, toPlaceRows, type ChargingPlace, type PlaceRow } from './storyPlaces'
import { chargeMonths, earliestOffset } from './storyMonthGrid'

/**
 * Laedt die Monatsdaten fuer das Story-Dashboard und leitet Kacheln und Feed ab.
 *
 * Eigene Abfragen statt des Dashboard-Zeitraums: das Story-Dashboard denkt in Monaten,
 * das alte Dashboard in frei waehlbaren Zeitraeumen. Beide teilen sich keinen localStorage.
 * Je Monat: drei Statistiken, Fahrten, Leaderboard. Je Auto einmal: Ersparnis, Peer-Modelle (alle Varianten),
 * preislose Ladungen, SoH-Verlauf, letzte Ladungen und Fahrten. Alles je Monat wartet gemeinsam, damit der Feed
 * nicht nach dem ersten Paint umsortiert, und ohne Cache auch auf die Daten je Auto, sonst springt der Hero vom
 * Monat zum Moment um und die Zuletzt-Karten rutschen nach.
 */
export interface StoryMonth {
  year: number
  month: number
  /** 0 = laufender Monat, -1 = Vormonat, -2 = davor. */
  offset: number
}

/** Fahrzeug- und Rechte-Kontext, den der View liefert (ein Record statt sechs Parameter). */
export interface StoryCarContext {
  wltpConsumption: number | null
  sohPercent: number | null
  odometerKm: number | null
  /** Fahrzeugklasse (VehicleCategory) für den Benziner im Vergleich */
  vehicleCategory: string | null
  effectiveBatteryCapacityKwh: number | null
  energySplitLocked: boolean
  canViewLiveAnalytics: boolean
  /** DC-Ladung laeuft und das Live-Gate ist offen. */
  liveActive: boolean
  /** Marke hat einen AutoSync-Weg und das Auto ist nicht verbunden. */
  autoSync: boolean
  /** Widerruf des Moment-Heros. */
  alwaysMonth: boolean
}

export const STORY_MONTH_OFFSETS = [-2, -1, 0] as const

/** Alle Leaderboard-Kategorien; das Backend kennt nur den laufenden und den Vormonat. */
export const LEADERBOARD_CATEGORIES = [
  'MONTHLY_KWH', 'MONTHLY_CHARGES', 'MONTHLY_DISTANCE', 'MONTHLY_CHEAPEST',
  'MONTHLY_NIGHT_OWL', 'MONTHLY_ICE_CHARGER', 'MONTHLY_HEAT_CHARGER', 'MONTHLY_POWER_CHARGER',
] as const

export function monthAt(offset: number, now = new Date()): StoryMonth {
  const d = new Date(now.getFullYear(), now.getMonth() + offset, 1)
  return { year: d.getFullYear(), month: d.getMonth() + 1, offset }
}

/** Letzte Ladungen fuer "gerade geladen", Stille und die Stopps einer langen Fahrt. */
const RECENT_LOGS = 10
/** Fahrten fuer lange Fahrt (24 h) und Frost (3 Tage). */
const MOMENT_TRIP_DAYS = 3

/** Fenster der Reichweiten-Kachel: 4 Wochen bis heute. */
export const RECENT_DAYS = 28
function daysAgo(n: number): Date { const d = new Date(); d.setHours(0, 0, 0, 0); d.setDate(d.getDate() - n); return d }
function isoDate(d: Date): string { return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}` }

async function fetchStats(carId: string, start: string, end: string, groupBy: 'DAY' | 'MONTH'): Promise<StatisticsData | null> {
  const params = new URLSearchParams({ carId, timeRange: 'CUSTOM', startDate: start, endDate: end, groupBy })
  try {
    const res = await api.get<StatisticsData>(`/logs/statistics?${params}`)
    return res.data
  } catch {
    return null
  }
}

/** Fahrten des Monats. Nicht Berechtigte bekommen vom Backend keine Fahrten, Fehler werden zu leer. */
async function fetchTrips(carId: string, start: string, end: string): Promise<TripForTemperature[]> {
  const from = new Date(`${start}T00:00:00Z`).toISOString()
  const to = new Date(new Date(`${end}T00:00:00Z`).getTime() + 86_400_000).toISOString()
  try {
    const res = await api.get<TripForTemperature[]>(`/trips?carId=${carId}&from=${from}&to=${to}`)
    return Array.isArray(res.data) ? res.data : []
  } catch {
    return []
  }
}

/** Ladungen vom Beginn des Vormonats bis Monatsende: die Ladung vor einem Stand am Monatsanfang liegt oft davor. */
async function fetchCharges(carId: string, start: string, end: string): Promise<StandbyPricing['charges']> {
  const from = new Date(`${start}T00:00:00Z`).toISOString()
  const to = new Date(new Date(`${end}T00:00:00Z`).getTime() + 86_400_000).toISOString()
  try {
    const res = await api.get<StandbyPricing['charges']>(`/logs?carId=${carId}&from=${from}&to=${to}`)
    return Array.isArray(res.data) ? res.data : []
  } catch {
    return []
  }
}

type RankResponse = { category: string; ownEntry: RankEntry | null } | null

async function fetchRanks(monthsBack: number): Promise<RankResponse[]> {
  const settled = await Promise.allSettled(LEADERBOARD_CATEGORIES.map(category =>
    api.get<{ ownEntry: RankEntry | null }>(`/public/leaderboard/${category}`, { params: { monthsBack } })
      .then(r => ({ category, ownEntry: r.data.ownEntry ?? null }))))
  return settled.map(s => s.status === 'fulfilled' ? s.value : null)
}

/** Alles, was ein Monat an Antworten braucht, als Einheit im Cache. */
interface MonthBundle {
  monthStats: StatisticsData | null
  previousStats: StatisticsData | null
  series: StatisticsData | null
  trips: TripForTemperature[]
  ranks: RankResponse[]
  /** Ladungen ab Vormonat, nur mit Freischaltung: Preis der Standverluste. */
  charges: StandbyPricing['charges']
  /** Ladeorte des Monats samt Vergleich je Betreiber und Ladeart: der Karten-Teaser wechselt mit dem Monat. */
  places: ChargingPlace[]
  benchmarks: Record<string, ChargingBenchmark | null>
}

/**
 * Modulweiter Cache je Auto und Monat: ein schon gesehener Monat erscheint sofort,
 * im Hintergrund wird er trotzdem neu geladen (stale while revalidate).
 */
const monthCache = new Map<string, MonthBundle>()
/** Laufende Abrufe je Auto und Monat: Vorladen und Wechsel teilen sich denselben. */
const monthInflight = new Map<string, Promise<MonthBundle | null>>()
/** Vergleiche je Betreiber und Ladeart, über Monate geteilt: dieselben Betreiber kommen in jedem Monat vor. */
const benchmarkCache = new Map<string, Promise<ChargingBenchmark | null>>()

function monthKey(carId: string, m: StoryMonth): string { return `${carId}:${m.year}-${m.month}` }

function fetchBenchmark(cpo: string, type: 'AC' | 'DC'): Promise<ChargingBenchmark | null> {
  const key = `${cpo}|${type}`
  let found = benchmarkCache.get(key)
  if (!found) {
    found = chargingBenchmarkService.get(cpo, type).catch(() => null)
    benchmarkCache.set(key, found)
  }
  return found
}

async function fetchMonthPlaces(carId: string, m: StoryMonth): Promise<Pick<MonthBundle, 'places' | 'benchmarks'>> {
  const w = monthWindow(m.year, m.month)
  try {
    const res = await api.get<ChargingPlace[]>('/logs/places', { params: { carId, from: w.start, to: w.end } })
    const places = Array.isArray(res.data) ? res.data : []
    const pairs = benchmarkPairs(places)
    const found = await Promise.all(pairs.map(p => fetchBenchmark(p.cpo, p.type)))
    return { places, benchmarks: Object.fromEntries(pairs.map((p, i) => [`${p.cpo}|${p.type}`, found[i]])) }
  } catch {
    return { places: [], benchmarks: {} }
  }
}

/** Alles eines Monats in einem Schritt. null, wenn die Monatsstatistik fehlt; der Rest fällt einzeln auf leer zurück. */
function fetchMonthBundle(carId: string, m: StoryMonth, withCharges: boolean): Promise<MonthBundle | null> {
  const key = monthKey(carId, m)
  const running = monthInflight.get(key)
  if (running) return running
  const job = (async () => {
    const cur = monthWindow(m.year, m.month)
    const prev = monthWindow(m.year, m.month, -1)
    const first = monthWindow(m.year, m.month, -5)
    const monthsBack = -m.offset
    const [a, b, c, t, r, ch, pl] = await Promise.all([
      fetchStats(carId, cur.start, cur.end, 'DAY'),
      fetchStats(carId, prev.start, prev.end, 'MONTH'),
      fetchStats(carId, first.start, cur.end, 'MONTH'),
      fetchTrips(carId, cur.start, cur.end),
      monthsBack <= 1 ? fetchRanks(monthsBack) : Promise.resolve([] as RankResponse[]),
      withCharges ? fetchCharges(carId, prev.start, cur.end) : Promise.resolve([]),
      fetchMonthPlaces(carId, m),
    ])
    return a == null ? null : { monthStats: a, previousStats: b, series: c, trips: t, ranks: r, charges: ch, ...pl }
  })().finally(() => monthInflight.delete(key))
  monthInflight.set(key, job)
  return job
}

/** Benzinpreis gilt fuer die ganze App, nicht je Auto: ein Abruf je Sitzung reicht. */
let fuelPricePromise: Promise<FuelPrices | null> | null = null
function fetchFuelPrices(): Promise<FuelPrices | null> {
  fuelPricePromise ??= fuelPriceService.get().catch(() => null)
  return fuelPricePromise
}

export interface PetrolPrice {
  eurPerLitre: number
  /** true, wenn der Preis vom Tankerkoenig-Abruf stammt und nicht aus einem Rueckfallwert. */
  live: boolean
}

/** Was je Auto einmal geladen wird, unabhaengig vom Monat: Basis von Moment-Hero, Zuletzt-Karten und Reichweite. */
interface CarBundle {
  savingsEur: number | null
  amortisationYearsRemaining: number | null
  peerModels: PeerModelComparisonResponse | null
  pricelessLogs: MomentLog[]
  sohHistory: BatterySohEntry[]
  petrol: PetrolPrice
  recentStats: StatisticsData | null
  recentLogs: Array<LatestLogSource & MomentLog>
  benchmark: ChargingBenchmark | null
  recentTrips: MomentTrip[]
  lifetimeSeries: MomentPoint[]
  latestTrip: LatestTripSource | null
}

/** Wie der Monats-Cache, nur je Auto: Hero und Zuletzt-Karten stehen beim naechsten Oeffnen sofort. */
const carCache = new Map<string, CarBundle>()

/**
 * Die letzten Ladungen und, wenn die neueste oeffentliche einen Vergleich lohnt, was andere
 * Fahrer dort zahlen. In einem Schritt, damit der Moment-Hero nicht nach dem ersten Paint wechselt.
 */
async function recentLogsWithBenchmark(carId: string): Promise<{ logs: Array<LatestLogSource & MomentLog>; benchmark: ChargingBenchmark | null }> {
  const res = await api.get<Array<LatestLogSource & MomentLog>>(`/logs?carId=${carId}&limit=${RECENT_LOGS}`)
  const logs = Array.isArray(res.data) ? res.data : []
  const c = expensiveCandidate(logs, Date.now())
  const cpo = c?.log.cpoName
  const type = c?.log.chargingType
  const bench = cpo && (type === 'AC' || type === 'DC')
    ? await chargingBenchmarkService.get(cpo, type).catch(() => null)
    : null
  return { logs, benchmark: bench }
}

async function fetchCarBundle(carId: string): Promise<CarBundle> {
  const tripsFrom = new Date(Date.now() - MOMENT_TRIP_DAYS * DAY).toISOString()
  const [savings, models, priceless, soh, fuel, lastLogs, lastTrip, recent, momentTrips, lifetime] = await Promise.allSettled([
    chargingSavingsService.get(),
    peerModelComparisonService.getPeerModelComparison(carId),
    api.get<MomentLog[]>(`/logs/priceless?carId=${carId}&includeUnknown=true`),
    carService.getSohHistory(carId),
    fetchFuelPrices(),
    recentLogsWithBenchmark(carId),
    api.get<LatestTripSource[]>(`/trips?carId=${carId}&limit=1`),
    fetchStats(carId, isoDate(daysAgo(RECENT_DAYS - 1)), isoDate(daysAgo(0)), 'MONTH'),
    api.get<MomentTrip[]>(`/trips?carId=${carId}&from=${encodeURIComponent(tripsFrom)}&to=${encodeURIComponent(new Date().toISOString())}`),
    api.get<StatisticsData>(`/logs/statistics?carId=${carId}&groupBy=DAY`),
  ])
  const prices = fuel.status === 'fulfilled' ? fuel.value : null
  return {
    petrol: prices && prices.petrolEurPerLitre > 0
      ? { eurPerLitre: prices.petrolEurPerLitre, live: prices.updatedAt != null }
      : { eurPerLitre: PETROL_EUR_PER_L_DE, live: false },
    savingsEur: savings.status === 'fulfilled' ? savings.value.savings?.savingsEur ?? null : null,
    amortisationYearsRemaining: savings.status === 'fulfilled' ? savings.value.savings?.amortisationYearsRemaining ?? null : null,
    peerModels: models.status === 'fulfilled' ? models.value : null,
    pricelessLogs: priceless.status === 'fulfilled' && Array.isArray(priceless.value.data) ? priceless.value.data : [],
    sohHistory: soh.status === 'fulfilled' ? soh.value : [],
    recentStats: recent.status === 'fulfilled' ? recent.value : null,
    recentLogs: lastLogs.status === 'fulfilled' ? lastLogs.value.logs : [],
    benchmark: lastLogs.status === 'fulfilled' ? lastLogs.value.benchmark : null,
    recentTrips: momentTrips.status === 'fulfilled' && Array.isArray(momentTrips.value.data) ? momentTrips.value.data : [],
    lifetimeSeries: lifetime.status === 'fulfilled' ? lifetime.value.data?.chargesOverTime ?? [] : [],
    // Fahrten liefert das Backend nur Berechtigten, ein Fehler heisst dann einfach: keine Fahrt.
    latestTrip: lastTrip.status === 'fulfilled' && Array.isArray(lastTrip.value.data) ? lastTrip.value.data[0] ?? null : null,
  }
}

export function clearStoryCache(): void {
  fuelPricePromise = null
  monthCache.clear()
  monthInflight.clear()
  benchmarkCache.clear()
  carCache.clear()
}

export function useDashboardStory(selectedCarId: Ref<string | null>, ctx: () => StoryCarContext) {
  /** Der gewählte Monat (Schalter), springt sofort. */
  const offset = ref(0)
  /** Der angezeigte Monat: wechselt erst zusammen mit seinen Daten (apply), sonst passten Tage und Zahlen nicht zusammen. */
  const month = ref<StoryMonth>(monthAt(0))
  /** Gewählt, aber noch nicht da: der bisherige Monat bleibt stehen, bis der neue komplett ist. */
  const switching = computed(() => offset.value !== month.value.offset)
  const months = computed(() => STORY_MONTH_OFFSETS.map(o => monthAt(o)))

  const monthStats = ref<StatisticsData | null>(null)
  const previousStats = ref<StatisticsData | null>(null)
  const series = ref<StatisticsData | null>(null)
  const trips = ref<TripForTemperature[]>([])
  const charges = ref<StandbyPricing['charges']>([])
  const ranks = ref<RankResponse[]>([])
  const places = ref<ChargingPlace[]>([])
  const placeBenchmarks = ref<Record<string, ChargingBenchmark | null>>({})
  const placeRows = computed<PlaceRow[]>(() => toPlaceRows(places.value, placeBenchmarks.value))
  const savingsEur = ref<number | null>(null)
  const amortisationYearsRemaining = ref<number | null>(null)
  const peerModels = ref<PeerModelComparisonResponse | null>(null)
  /** Alle Ladungen ohne Preis, auch "Weiß ich nicht mehr": die Kosten des Monats bleiben unvollständig. */
  const pricelessLogs = ref<MomentLog[]>([])
  const sohHistory = ref<BatterySohEntry[]>([])
  /** Statistik der letzten 4 Wochen, Basis der Reichweiten-Kachel. */
  const recentStats = ref<StatisticsData | null>(null)
  const petrol = ref<PetrolPrice>({ eurPerLitre: PETROL_EUR_PER_L_DE, live: false })
  const latest = ref<StoryLatest | null>(null)
  const recentLogs = ref<Array<LatestLogSource & MomentLog>>([])
  const recentTrips = ref<MomentTrip[]>([])
  /** Tagesreihe ueber die ganze Lebenszeit, Basis der Meilensteine. */
  const lifetimeSeries = ref<MomentPoint[]>([])
  /** Was andere Fahrer beim Betreiber der neuesten oeffentlichen Ladung zahlen, null ohne Vergleich. */
  const benchmark = ref<ChargingBenchmark | null>(null)
  const { seen } = useMomentSeen()
  const loading = ref(true)
  const error = ref(false)

  let requestId = 0
  let carRequestId = 0
  /** Steht fuer "Daten je Auto sind da"; mit Cache-Treffer sofort erfuellt. */
  let carReady: Promise<void> = Promise.resolve()

  function apply(b: MonthBundle, m: StoryMonth) {
    month.value = m
    monthStats.value = b.monthStats
    previousStats.value = b.previousStats
    series.value = b.series
    trips.value = b.trips
    ranks.value = b.ranks
    charges.value = b.charges
    places.value = b.places
    placeBenchmarks.value = b.benchmarks
  }

  async function load() {
    const carId = selectedCarId.value
    if (!carId) { loading.value = false; return }
    const id = ++requestId
    const m = monthAt(offset.value)
    const key = monthKey(carId, m)
    const hit = monthCache.get(key)
    if (hit) {
      apply(hit, m)
      loading.value = false
    } else {
      loading.value = true
    }
    error.value = false
    const bundle = await fetchMonthBundle(carId, m, ctx().canViewLiveAnalytics)
    if (id !== requestId) return
    if (!bundle) {
      // Hintergrund-Reload gescheitert: der gecachte Stand bleibt stehen, sonst Fehlerzustand.
      if (!hit) error.value = true
      loading.value = false
      return
    }
    monthCache.set(key, bundle)
    // Erst zeigen, wenn auch die Daten je Auto da sind: Hero und Zuletzt-Karten kommen in einem Paint.
    await carReady
    if (id !== requestId) return
    apply(bundle, m)
    loading.value = false
    prefetchMonths(carId, m.offset)
  }

  /**
   * Nach dem ersten Paint nacheinander vorladen, was als Nächstes kommt: die Monate des Schalters und die Nachbarn des
   * gezeigten (Wischen auf dem Hero, ältere Monate aus dem Raster). Der Wechsel steht dann sofort.
   */
  async function prefetchMonths(carId: string, shown: number) {
    const next = new Set<number>([...STORY_MONTH_OFFSETS, shown - 1, shown + 1])
    for (const o of next) {
      if (o > 0 || o < minOffset.value) continue
      const m = monthAt(o)
      const key = monthKey(carId, m)
      if (monthCache.has(key)) continue
      if (carId !== selectedCarId.value) return
      const b = await fetchMonthBundle(carId, m, ctx().canViewLiveAnalytics)
      if (b) monthCache.set(key, b)
    }
  }

  function applyCar(b: CarBundle) {
    petrol.value = b.petrol
    savingsEur.value = b.savingsEur
    amortisationYearsRemaining.value = b.amortisationYearsRemaining
    peerModels.value = b.peerModels
    pricelessLogs.value = b.pricelessLogs
    sohHistory.value = b.sohHistory
    recentStats.value = b.recentStats
    recentLogs.value = b.recentLogs
    benchmark.value = b.benchmark
    recentTrips.value = b.recentTrips
    lifetimeSeries.value = b.lifetimeSeries
    latest.value = buildLatest(b.recentLogs[0] ?? null, b.latestTrip)
  }

  /** Gecachter Stand sofort, frischer im Hintergrund (stale while revalidate). */
  function loadCar(carId: string): Promise<void> {
    const id = ++carRequestId
    const hit = carCache.get(carId)
    if (hit) applyCar(hit)
    const fresh = fetchCarBundle(carId).then((b) => {
      if (id !== carRequestId) return
      carCache.set(carId, b)
      applyCar(b)
    })
    // Scheitert der Abruf, zeigt die Seite trotzdem den Monat, statt im Skeleton zu haengen.
    return hit ? Promise.resolve() : fresh.catch(() => undefined)
  }

  /** Monat und Daten je Auto: nach einer neuen oder geaenderten Ladung stimmen sonst Hero und Zuletzt-Karten nicht. */
  function reload(): Promise<void> {
    if (selectedCarId.value) carReady = loadCar(selectedCarId.value)
    return load()
  }

  // flush sync: ein Cache-Treffer steht noch im selben Tick, bevor Vue rendert. Die Daten je Auto zuerst,
  // load() wartet auf carReady.
  watch(selectedCarId, (id) => { carReady = id ? loadCar(id) : Promise.resolve() }, { immediate: true, flush: 'sync' })
  watch([selectedCarId, offset], () => { load() }, { immediate: true, flush: 'sync' })

  /** Sechs Monatswerte, aeltester zuerst. Fehlende Monate werden mit 0 bzw. null aufgefuellt. */
  function monthlySeries(pick: (p: StatisticsData['chargesOverTime'][number]) => number | null): number[] {
    const m = month.value
    const points = series.value?.chargesOverTime ?? []
    return Array.from({ length: 6 }, (_, i) => {
      const d = new Date(m.year, m.month - 1 - (5 - i), 1)
      const hit = points.find(p => {
        const t = new Date(p.timestamp)
        return t.getFullYear() === d.getFullYear() && t.getMonth() === d.getMonth()
      })
      return hit ? (pick(hit) ?? 0) : 0
    })
  }

  const flow = computed(() => monthStats.value ? buildEnergyFlow(monthStats.value) : null)
  const range = computed(() => recentStats.value
    ? buildRange(recentStats.value, ctx().effectiveBatteryCapacityKwh)
    : null)
  const layers = computed(() => monthStats.value ? costLayers(monthStats.value) : null)
  /** Die Sechs-Monats-Statistik summiert Fixkosten und Einnahmen über alle sechs Balken. */
  const hasFixedCosts = computed(() => (series.value?.fixedCostEur ?? 0) > 0 || (series.value?.fixedIncomeEur ?? 0) > 0)

  /**
   * Strom und Fixkosten je Monat der sechs Balken, aeltester zuerst. Die Monatsreihe kennt nur Ladungen,
   * die Fixkosten stehen nur in der Statistik eines Zeitraums: deshalb eine Statistik je Monat, erst
   * wenn das Kosten-Sheet sie zeigt. Null, wenn das Auto inzwischen gewechselt hat.
   */
  async function loadMonthlyCosts(): Promise<MonthCosts[] | null> {
    const carId = selectedCarId.value
    if (!carId) return null
    const m = month.value
    const windows = Array.from({ length: 6 }, (_, i) => monthWindow(m.year, m.month, i - 5))
    const all = await Promise.all(windows.map(w => fetchStats(carId, w.start, w.end, 'MONTH')))
    return carId === selectedCarId.value ? all.map(monthCosts) : null
  }
  const temperature = computed(() => temperatureEffect(trips.value, ctx().effectiveBatteryCapacityKwh))

  /** Lifetime gegen Lifetime: eigener Wert aus dem Peer-Modell-Vergleich, sonst der Monat. Preis und Verbrauch aus derselben Quelle. */
  const peers = computed(() => {
    const own = peerModels.value?.modelComparisons?.find(x => x.isUserVehicleSpec)?.userMetrics
    const lifetime = own?.costPerKwh != null && own.consumptionKwhPer100km != null
      ? { costPerKwh: own.costPerKwh, consumption: own.consumptionKwhPer100km }
      : null
    const m = monthStats.value
    const monthly = m ? { costPerKwh: m.avgCostPerKwh, consumption: m.avgConsumptionKwhPer100km } : null
    return modelPeers(peerModels.value, lifetime ?? monthly)
  })

  const quality = computed(() => monthStats.value
    ? dataQuality(monthStats.value,
      countPricelessInMonth(pricelessLogs.value, month.value.year, month.value.month),
      countPricelessInMonth(pricelessLogs.value.filter(l => !l.priceUnknown), month.value.year, month.value.month))
    : null)

  const pricelessInPreviousMonth = computed(() => {
    const prev = new Date(month.value.year, month.value.month - 2, 1)
    return countPricelessInMonth(pricelessLogs.value, prev.getFullYear(), prev.getMonth() + 1)
  })
  const input = computed(() => monthStats.value ? {
    month: monthStats.value,
    previousMonth: previousStats.value,
    monthlyCosts: monthlySeries(p => p.costEur),
    monthlyConsumption: monthlySeries(p => p.consumptionKwhPer100km),
    wltpConsumption: ctx().wltpConsumption,
    sohPercent: ctx().sohPercent,
    sohEstimated: sohIsEstimated(sohHistory.value),
    odometerKm: ctx().odometerKm,
    savingsEur: savingsEur.value,
    amortisationYearsRemaining: amortisationYearsRemaining.value,
    energySplitLocked: ctx().energySplitLocked,
    flow: flow.value,
    range: range.value,
    modelPeers: peers.value,
    rank: bestRank(ranks.value),
    quality: quality.value,
    temperature: temperature.value,
    temperatureLocked: !ctx().canViewLiveAnalytics && temperature.value == null,
    pricelessPrevious: pricelessInPreviousMonth.value,
    vehicleCategory: ctx().vehicleCategory,
    lifetimeSeries: lifetimeSeries.value,
    runningMonth: month.value.offset === 0,
    tripMonth: buildTripMonth(trips.value, ctx().effectiveBatteryCapacityKwh, ctx().canViewLiveAnalytics,
      { charges: charges.value, avgCostPerKwh: monthStats.value?.avgCostPerKwh ?? null }),
  } : null)

  /**
   * Moment-Hero: nur im laufenden Monat, sonst steht der gewaehlte Monat oben. Live gewinnt immer,
   * auch mit "Immer den Monat zeigen" (wie vor dem Moment-Hero).
   */
  const moments = computed(() => {
    const c = ctx()
    const now = Date.now()
    const live = detectLive(c.liveActive)
    if (month.value.offset !== 0 || c.alwaysMonth) return pickMoment(live ? [live] : [], now, seen.value)
    const lastLog = recentLogs.value[0] ?? null
    const prev = previousStats.value
    const prevMonth = new Date(month.value.year, month.value.month - 2, 1)
    const justCharged = detectJustCharged({ log: lastLog, now, consumptionKwh100: range.value?.consumption ?? null })
    const candidates = [
      live,
      justCharged,
      detectExpensive({ logs: recentLogs.value, benchmark: benchmark.value, now }),
      detectLongTrip({ trips: recentTrips.value, logs: recentLogs.value, now }),
      detectFirstFrost({ trips: recentTrips.value, now, range: range.value }),
      detectMonthChange({
        now,
        previous: prev ? {
          year: prevMonth.getFullYear(), month: prevMonth.getMonth() + 1,
          distanceKm: prev.totalDistanceKm, energyCostEur: prev.energyCostEur, charges: prev.totalCharges,
          priceless: pricelessInPreviousMonth.value,
        } : null,
        petrolEurPerLitre: petrol.value.eurPerLitre,
        petrolLitersPer100km: petrolLitersPer100km(c.vehicleCategory),
      }),
      detectBattery({ history: sohHistory.value, now }),
      detectRank({ rank: bestRank(ranks.value), now }),
      detectSilence({ log: lastLog, now, autoSync: c.autoSync }),
      // Die gerade geladene fragt schon selbst nach ihrem Preis.
      detectMilestone({ series: lifetimeSeries.value, now, petrolLitersPer100km: petrolLitersPer100km(c.vehicleCategory) }),
      detectGap({ logs: pricelessLogs.value, now, skipLogId: justCharged?.kind === 'just_charged' ? justCharged.logId : null }),
    ].filter((m): m is Moment => m != null)
    return pickMoment(candidates, now, seen.value)
  })
  const moment = computed<Moment>(() => moments.value.hero)

  const tiles = computed<StoryTile[]>(() => input.value ? buildStoryTiles(input.value) : [])
  // Nur lokal: /story?feed=alle zeigt alle Sätze, die die Daten hergeben, statt der fünf relevantesten.
  const allFeed = import.meta.env.DEV && typeof window !== 'undefined' && new URLSearchParams(window.location.search).get('feed') === 'alle'
  const feed = computed<StoryFeedItem[]>(() => input.value
    ? buildStoryFeed(input.value, {
      extra: momentFeedItems(moments.value.rest),
      // Was der Hero sagt, steht nicht noch einmal im Feed.
      omit: moment.value.kind === 'rank' ? ['community_rank'] : [],
      max: allFeed ? Infinity : undefined,
    })
    : [])
  const days = computed<DayCoverage[]>(() =>
    buildDayCoverage(monthStats.value?.chargesOverTime ?? [], month.value.year, month.value.month, Date.now(), trips.value))
  const hasCharges = computed(() => (monthStats.value?.totalCharges ?? 0) > 0)

  /** "Weiß ich nicht mehr" aus dem Hero: die Frage verschwindet sofort, die Ladung bleibt ohne Preis. */
  async function markPriceUnknown(logId: string): Promise<boolean> {
    try {
      await api.patch(`/logs/${logId}/gap`, { field: 'PRICE' })
      pricelessLogs.value = pricelessLogs.value.map(l => l.id === logId ? { ...l, priceUnknown: true } : l)
      const carId = selectedCarId.value
      const cached = carId ? carCache.get(carId) : undefined
      if (carId && cached) carCache.set(carId, { ...cached, pricelessLogs: pricelessLogs.value })
      return true
    } catch {
      return false
    }
  }

  /** Monate mit Ladungen und der früheste wählbare (Monatsraster), aus der Tagesreihe über die Lebenszeit. */
  const dataMonths = computed(() => chargeMonths(lifetimeSeries.value))
  const minOffset = computed(() => earliestOffset(dataMonths.value, new Date()))

  /** Die drei Monate des Schalters und über das Raster jeder ältere bis zur ersten Ladung. */
  function setOffset(o: number) {
    if (Number.isInteger(o) && o <= 0 && o >= minOffset.value) offset.value = o
  }

  return {
    offset, month, months, setOffset, switching, placeRows, dataMonths, minOffset,
    monthStats, previousStats, tiles, feed, days, hasCharges, loading, error,
    layers, hasFixedCosts, loadMonthlyCosts, quality, temperature, modelPeers: peers, sohHistory, petrol, latest, moment, benchmark,
    reload, markPriceUnknown,
  }
}
