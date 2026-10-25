import { ref, computed, watch, type Ref } from 'vue'
import api from '../api/axios'
import chargingSavingsService from '../api/chargingSavingsService'
import { peerModelComparisonService, type PeerModelComparisonItem } from '../api/peerModelComparisonService'
import { carService, type BatterySohEntry } from '../api/carService'
import { fuelPriceService, type FuelPrices } from '../api/fuelPriceService'
import { chargingBenchmarkService, type ChargingBenchmark } from '../api/chargingBenchmarkService'
import type { StatisticsData } from './useDashboardStats'
import { calcCostPer100km } from './useDashboardStats'
import {
  buildStoryTiles, buildStoryFeed, buildDayCoverage, monthWindow, PETROL_EUR_PER_L_DE,
  type StoryTile, type StoryFeedItem, type DayCoverage,
} from './storyModel'
import { buildEnergyFlow, buildRange, costLayers } from './storyEnergy'
import {
  temperatureEffect, whatIfModels, dataQuality, countPricelessInMonth, bestRank,
  type TripForTemperature, type RankEntry, buildLatest, sohIsEstimated, type StoryLatest, type LatestLogSource, type LatestTripSource } from './storyInsights'
import {
  detectLive, detectJustCharged, detectLongTrip, detectFirstFrost, detectMonthChange, detectBattery, detectRank,
  detectSilence, detectGap, detectMilestone, detectExpensive, expensiveCandidate, pickMoment, momentFeedItems, DAY,
  type Moment, type MomentLog, type MomentTrip, type MomentPoint,
} from './storyMoments'
import { useMomentSeen } from './useMomentSeen'

/**
 * Laedt die Monatsdaten fuer das Story-Dashboard und leitet Kacheln und Feed ab.
 *
 * Eigene Abfragen statt des Dashboard-Zeitraums: das Story-Dashboard denkt in Monaten,
 * das alte Dashboard in frei waehlbaren Zeitraeumen. Beide teilen sich keinen localStorage.
 * Je Monat: drei Statistiken, Fahrten, Leaderboard. Je Auto einmal: Ersparnis, Peer-Modelle,
 * preislose Ladungen, SoH-Verlauf. Alles je Monat wartet gemeinsam, damit der Feed nicht
 * nach dem ersten Paint umsortiert.
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

function monthAt(offset: number, now = new Date()): StoryMonth {
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
}

/**
 * Modulweiter Cache je Auto und Monat: ein schon gesehener Monat erscheint sofort,
 * im Hintergrund wird er trotzdem neu geladen (stale while revalidate).
 */
const monthCache = new Map<string, MonthBundle>()

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

export function clearStoryCache(): void {
  fuelPricePromise = null
  monthCache.clear()
}

export function useDashboardStory(selectedCarId: Ref<string | null>, ctx: () => StoryCarContext) {
  const offset = ref(0)
  const month = computed(() => monthAt(offset.value))
  const months = computed(() => STORY_MONTH_OFFSETS.map(o => monthAt(o)))

  const monthStats = ref<StatisticsData | null>(null)
  const previousStats = ref<StatisticsData | null>(null)
  const series = ref<StatisticsData | null>(null)
  const trips = ref<TripForTemperature[]>([])
  const ranks = ref<RankResponse[]>([])
  const savingsEur = ref<number | null>(null)
  const amortisationYearsRemaining = ref<number | null>(null)
  const peerModels = ref<PeerModelComparisonItem[]>([])
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

  function apply(b: MonthBundle) {
    monthStats.value = b.monthStats
    previousStats.value = b.previousStats
    series.value = b.series
    trips.value = b.trips
    ranks.value = b.ranks
  }

  async function load() {
    const carId = selectedCarId.value
    if (!carId) { loading.value = false; return }
    const id = ++requestId
    const m = month.value
    const key = `${carId}:${m.year}-${m.month}`
    const hit = monthCache.get(key)
    if (hit) {
      apply(hit)
      loading.value = false
    } else {
      loading.value = true
    }
    error.value = false
    const cur = monthWindow(m.year, m.month)
    const prev = monthWindow(m.year, m.month, -1)
    const first = monthWindow(m.year, m.month, -5)
    const monthsBack = -m.offset
    const [a, b, c, t, r] = await Promise.all([
      fetchStats(carId, cur.start, cur.end, 'DAY'),
      fetchStats(carId, prev.start, prev.end, 'MONTH'),
      fetchStats(carId, first.start, cur.end, 'MONTH'),
      fetchTrips(carId, cur.start, cur.end),
      monthsBack <= 1 ? fetchRanks(monthsBack) : Promise.resolve([] as RankResponse[]),
    ])
    if (id !== requestId) return
    if (a == null) {
      // Hintergrund-Reload gescheitert: der gecachte Stand bleibt stehen, sonst Fehlerzustand.
      if (!hit) error.value = true
      loading.value = false
      return
    }
    const bundle: MonthBundle = { monthStats: a, previousStats: b, series: c, trips: t, ranks: r }
    monthCache.set(key, bundle)
    apply(bundle)
    loading.value = false
  }

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

  async function loadPerCar(carId: string) {
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
    if (carId !== selectedCarId.value) return
    const prices = fuel.status === 'fulfilled' ? fuel.value : null
    petrol.value = prices && prices.petrolEurPerLitre > 0
      ? { eurPerLitre: prices.petrolEurPerLitre, live: prices.updatedAt != null }
      : { eurPerLitre: PETROL_EUR_PER_L_DE, live: false }
    savingsEur.value = savings.status === 'fulfilled' ? savings.value.savings?.savingsEur ?? null : null
    amortisationYearsRemaining.value = savings.status === 'fulfilled' ? savings.value.savings?.amortisationYearsRemaining ?? null : null
    peerModels.value = models.status === 'fulfilled' ? models.value.modelComparisons ?? [] : []
    pricelessLogs.value = priceless.status === 'fulfilled' && Array.isArray(priceless.value.data) ? priceless.value.data : []
    sohHistory.value = soh.status === 'fulfilled' ? soh.value : []
    recentStats.value = recent.status === 'fulfilled' ? recent.value : null
    recentLogs.value = lastLogs.status === 'fulfilled' ? lastLogs.value.logs : []
    benchmark.value = lastLogs.status === 'fulfilled' ? lastLogs.value.benchmark : null
    recentTrips.value = momentTrips.status === 'fulfilled' && Array.isArray(momentTrips.value.data) ? momentTrips.value.data : []
    lifetimeSeries.value = lifetime.status === 'fulfilled' ? lifetime.value.data?.chargesOverTime ?? [] : []
    // Fahrten liefert das Backend nur Berechtigten, ein Fehler heisst dann einfach: keine Fahrt.
    latest.value = buildLatest(
      recentLogs.value[0] ?? null,
      lastTrip.status === 'fulfilled' && Array.isArray(lastTrip.value.data) ? lastTrip.value.data[0] : null,
    )
  }

  // flush sync: ein Cache-Treffer steht noch im selben Tick, bevor Vue rendert.
  watch([selectedCarId, offset], () => { load() }, { immediate: true, flush: 'sync' })
  watch(selectedCarId, (id) => { if (id) loadPerCar(id) }, { immediate: true })

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
  const temperature = computed(() => temperatureEffect(trips.value, ctx().effectiveBatteryCapacityKwh))

  /** Lifetime gegen Lifetime: eigener Wert aus dem Peer-Modell-Vergleich, sonst der Monat. */
  const whatIf = computed(() => {
    const own = peerModels.value.find(x => x.isUserVehicleSpec)?.userMetrics
    const lifetime = own ? calcCostPer100km(own.costPerKwh, own.consumptionKwhPer100km) : null
    const monthly = monthStats.value ? calcCostPer100km(monthStats.value.avgCostPerKwh, monthStats.value.avgConsumptionKwhPer100km) : null
    return whatIfModels(peerModels.value, lifetime ?? monthly)
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
    whatIf: whatIf.value,
    rank: bestRank(ranks.value),
    quality: quality.value,
    temperature: temperature.value,
    temperatureLocked: !ctx().canViewLiveAnalytics && temperature.value == null,
    pricelessPrevious: pricelessInPreviousMonth.value,
    runningMonth: month.value.offset === 0,
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
      }),
      detectBattery({ history: sohHistory.value, now }),
      detectRank({ rank: bestRank(ranks.value), now }),
      detectSilence({ log: lastLog, now, autoSync: c.autoSync }),
      // Die gerade geladene fragt schon selbst nach ihrem Preis.
      detectMilestone({ series: lifetimeSeries.value, now }),
      detectGap({ logs: pricelessLogs.value, now, skipLogId: justCharged?.kind === 'just_charged' ? justCharged.logId : null }),
    ].filter((m): m is Moment => m != null)
    return pickMoment(candidates, now, seen.value)
  })
  const moment = computed<Moment>(() => moments.value.hero)

  const tiles = computed<StoryTile[]>(() => input.value ? buildStoryTiles(input.value) : [])
  const feed = computed<StoryFeedItem[]>(() => input.value
    ? buildStoryFeed(input.value, {
      extra: momentFeedItems(moments.value.rest),
      // Was der Hero sagt, steht nicht noch einmal im Feed.
      omit: moment.value.kind === 'rank' ? ['community_rank'] : [],
    })
    : [])
  const days = computed<DayCoverage[]>(() =>
    buildDayCoverage(monthStats.value?.chargesOverTime ?? [], month.value.year, month.value.month))
  const hasCharges = computed(() => (monthStats.value?.totalCharges ?? 0) > 0)

  /** "Weiß ich nicht mehr" aus dem Hero: die Frage verschwindet sofort, die Ladung bleibt ohne Preis. */
  async function markPriceUnknown(logId: string): Promise<boolean> {
    try {
      await api.patch(`/logs/${logId}/gap`, { field: 'PRICE' })
      pricelessLogs.value = pricelessLogs.value.map(l => l.id === logId ? { ...l, priceUnknown: true } : l)
      return true
    } catch {
      return false
    }
  }

  function setOffset(o: number) {
    if (STORY_MONTH_OFFSETS.includes(o as typeof STORY_MONTH_OFFSETS[number])) offset.value = o
  }

  return {
    offset, month, months, setOffset,
    monthStats, previousStats, tiles, feed, days, hasCharges, loading, error,
    layers, quality, temperature, whatIf, sohHistory, petrol, latest, moment, benchmark,
    reload: load, markPriceUnknown,
  }
}
