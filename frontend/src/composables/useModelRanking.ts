import { computed, ref, watch, type Ref } from 'vue'
import type { ChargingReferencePrices, ModelWithoutData, TopModelPreview } from '../api/publicModelService'
import { personalRangeService, type PersonalRangeRankingEntry } from '../api/personalRangeService'
import { consumptionDeltaPercent } from '../utils/unitConversions'
import {
  assessEntry, summarizeNeeds, commuteKmPerWeek, meetsNeeds, fewestTripStops, NEEDS_EMPTY, MIN_COMMUTE_DAYS, MAX_COMMUTE_DAYS, COMMUTE_WEEKS_PER_YEAR,
  type NeedsAssessment, type NeedsInput, type NeedsSummary,
} from '../utils/needsCheck'
import { needsToInputs } from '../utils/drivingProfile'
import {
  COST_DEFAULTS, PRICE_MAX, effectiveHomeShare, mixedPricePerKwh, combustionLiters, savingsPer100km, savingsPerYear, isCostAssumptions,
  type CostAssumptions,
} from '../utils/costMix'

export type RankingSort = 'efficient' | 'range' | 'wltp' | 'winter' | 'data'

export const RANKING_SORTS: RankingSort[] = ['efficient', 'range', 'wltp', 'winter', 'data']
export const MAX_COMPARE = 3
/** Key of the classic model list; read once as the initial home price, never written here. */
export const PRICE_STORAGE_KEY = 'ev-price-per-kwh'
/** Cost assumptions stay in the browser only, nothing is sent to the server. */
export const COST_STORAGE_KEY = 'ev-cost-assumptions-v2'
/** The chosen sort stays in the browser, so a returning reader finds the list as left. */
export const SORT_STORAGE_KEY = 'ev-ranking-sort'
/**
 * Needs-check answers stay in the browser only, nothing is sent to the server. v2: every answer
 * may be empty. The old key 'ev-needs-check' is ignored, its values were defaults, not answers.
 */
export const NEEDS_STORAGE_KEY = 'ev-needs-check-v2'
export const NEEDS_KM_MAX = 5000
/** Upper bound for a stored yearly mileage; the slider ends at 50.000 km */
export const NEEDS_ANNUAL_KM_MAX = 200_000
/** Pause after the last answer before the list asks the server again */
export const RANKING_DEBOUNCE_MS = 300

export interface RankedModel {
  /** `Brand/Slug`, the format the compare page expects in `?models=` */
  key: string
  rank: number
  model: TopModelPreview
  /** WLTP reference: mean of the spec variants, or the middle of min and max */
  wltpKwhPer100km: number | null
  /** Real vs. WLTP in percent, positive = more consumption than the spec */
  wltpDeviationPct: number | null
  /** Winter vs. summer consumption in percent */
  winterSurchargePct: number | null
  costPer100kmEur: number | null
  /** Against the assumed combustion car, positive = EV cheaper; null without a fuel price */
  savingsPer100kmEur: number | null
  /** On the commute (km a day × days × COMMUTE_WEEKS_PER_YEAR); null until the commute is answered */
  savingsPerYearEur: number | null
  /** Charging cost a month at the reader's mileage (km a year, else the commute), from the server; null until one is answered */
  monthlyCostEur: number | null
  /** The monthly distance that cost rests on */
  monthlyKm: number | null
  /** Litres per 100 km the comparison assumed for this model's class; null without a comparison */
  combustionLitersPer100km: number | null
  /** All values of this model come from one driver */
  singleDriver: boolean
  /** The reader's figures from the server (same calculation as the model page); null without a server answer */
  needs: NeedsAssessment | null
  /** Reach on the motorway without a stop, with one and with two fast-charging stops on the largest battery (model data, from the server); null without */
  ladder: { zeroStopKm: number; oneStopKm: number; twoStopsKm: number } | null
}

export function modelKey(m: Pick<TopModelPreview, 'brandSlug' | 'modelUrlSlug'>): string {
  return `${m.brandSlug}/${m.modelUrlSlug}`
}

function wltpReference(m: TopModelPreview): number | null {
  if (m.avgWltpConsumptionKwhPer100km != null) return m.avgWltpConsumptionKwhPer100km
  const lo = m.minWltpConsumptionKwhPer100km
  const hi = m.maxWltpConsumptionKwhPer100km
  if (lo != null && hi != null) return (lo + hi) / 2
  return lo ?? hi ?? null
}

function wltpDeviation(m: TopModelPreview): number | null {
  const wltp = wltpReference(m)
  const real = m.avgConsumptionKwhPer100km
  return wltp != null && wltp > 0 && real != null ? consumptionDeltaPercent(real, wltp) : null
}

function winterSurcharge(m: TopModelPreview): number | null {
  const summer = m.summerConsumptionKwhPer100km
  const winter = m.winterConsumptionKwhPer100km
  return summer != null && summer > 0 && winter != null ? ((winter - summer) / summer) * 100 : null
}

/** Same matching as the classic list: every search term must appear in "brand model". */
function matchesSearch(m: Pick<TopModelPreview, 'brandDisplayName' | 'modelDisplayName'>, query: string): boolean {
  const q = query.trim().toLowerCase()
  if (!q) return true
  const haystack = `${m.brandDisplayName} ${m.modelDisplayName}`.toLowerCase().replace(/_/g, ' ')
  return q.split(/\s+/).every(term => haystack.includes(term))
}

// Sort value per mode; null always sorts last. Ascending unless listed in DESCENDING.
const SORT_VALUE: Record<RankingSort, (r: Omit<RankedModel, 'rank'>) => number | null> = {
  efficient: r => r.model.avgConsumptionKwhPer100km,
  // What the range column shows: typical range of the largest battery, real range as fallback
  range: r => r.model.typicalRangeMaxKm ?? r.model.realRangeKm,
  wltp: r => (r.wltpDeviationPct != null ? Math.abs(r.wltpDeviationPct) : null),
  winter: r => r.winterSurchargePct,
  data: r => r.model.logCount,
}
const DESCENDING: RankingSort[] = ['range', 'data']

/** Each stored answer on its own: a broken value is dropped (unanswered), the others stay. */
function parseNeeds(v: unknown): NeedsInput | null {
  if (!v || typeof v !== 'object') return null
  const o = v as Record<string, unknown>
  const km = (x: unknown) => typeof x === 'number' && Number.isFinite(x) && x >= 0 && x <= NEEDS_KM_MAX ? x : null
  const days = (x: unknown) => Number.isInteger(x) && (x as number) >= MIN_COMMUTE_DAYS && (x as number) <= MAX_COMMUTE_DAYS ? x as number : null
  return {
    annualKm: typeof o.annualKm === 'number' && Number.isFinite(o.annualKm) && o.annualKm > 0 && o.annualKm <= NEEDS_ANNUAL_KM_MAX ? o.annualKm : null,
    highwayKm: typeof o.highwayKm === 'number' && Number.isFinite(o.highwayKm) && o.highwayKm >= 0 && o.highwayKm <= NEEDS_ANNUAL_KM_MAX ? o.highwayKm : null,
    dailyKm: km(o.dailyKm),
    commuteDays: days(o.commuteDays),
    longestTripKm: km(o.longestTripKm),
    homeCharging: typeof o.homeCharging === 'boolean' ? o.homeCharging : null,
    // the stop selector is gone: whatever was stored, the threshold is the fixed default
    maxStops: NEEDS_EMPTY.maxStops,
    priority: o.priority === 'cost' || o.priority === 'breaks' ? o.priority : 'range',
  }
}

export function readStoredNeeds(): NeedsInput | null {
  try {
    const raw = localStorage.getItem(NEEDS_STORAGE_KEY)
    return raw === null ? null : parseNeeds(JSON.parse(raw))
  } catch {
    return null
  }
}

/** The model page hands its answers (yearly km, motorway, commute, days, longest trip, home charging) back to the ranking's needs check. */
export function writeStoredNeeds(patch: Partial<Omit<NeedsInput, 'maxStops' | 'priority'>>): void {
  const current = readStoredNeeds() ?? { ...NEEDS_EMPTY }
  try { localStorage.setItem(NEEDS_STORAGE_KEY, JSON.stringify({ ...current, ...patch })) } catch { /* private mode */ }
}

/** Stored assumptions, or just the classic list's price as home price (then nothing else is seeded yet). */
function readStoredCost(): { cost: CostAssumptions; seeded: boolean } | null {
  try {
    const raw = localStorage.getItem(COST_STORAGE_KEY)
    if (raw !== null) {
      const parsed: unknown = JSON.parse(raw)
      return isCostAssumptions(parsed) ? { cost: parsed, seeded: true } : null
    }
    const legacy = Number(localStorage.getItem(PRICE_STORAGE_KEY))
    if (Number.isFinite(legacy) && legacy > 0 && legacy <= PRICE_MAX) {
      return { cost: { ...COST_DEFAULTS, homePricePerKwh: legacy }, seeded: false }
    }
    return null
  } catch {
    return null
  }
}

function readStoredSort(): RankingSort {
  try {
    const raw = localStorage.getItem(SORT_STORAGE_KEY)
    return (RANKING_SORTS as string[]).includes(raw ?? '') ? raw as RankingSort : 'efficient'
  } catch {
    return 'efficient'
  }
}

const round2 = (v: number) => Math.round(v * 100) / 100
/** Electricity prices to a tenth of a cent, as the answers name them ("24,2 ct/kWh") */
const round3 = (v: number) => Math.round(v * 1000) / 1000

/**
 * State of the model ranking: sort mode, class filter, search, compare picks, needs check and
 * cost assumptions (blended electricity price, combustion comparison). UI logic on top of the
 * top-models DTO; the reader's figures per model come from the server's ranking endpoint, no
 * consumption or range formula of its own.
 */
export function useModelRanking(models: Ref<TopModelPreview[]>, modelsWithoutData: Ref<ModelWithoutData[]> = ref([])) {
  const sort = ref<RankingSort>(readStoredSort())
  watch(sort, s => {
    try { localStorage.setItem(SORT_STORAGE_KEY, s) } catch { /* private mode */ }
  }, { flush: 'sync' })
  const category = ref<string | null>(null)
  const query = ref('')
  /** Only models that make the longest trip with the fewest stops any model needs (fitStops) */
  const tripOnly = ref(false)

  const needs = ref<NeedsInput>(readStoredNeeds() ?? { ...NEEDS_EMPTY })
  watch(needs, v => {
    try { localStorage.setItem(NEEDS_STORAGE_KEY, JSON.stringify(v)) } catch { /* private mode */ }
  }, { deep: true })

  // The figures per model come from the server, on the same calculation as the model page. Asked once
  // on start and again after a pause once the calculator inputs change; the figures shown stay until
  // the new ones arrive. A reply to an older profile is dropped. Without a reply the rows show no
  // personal figures, the model data stays.
  const entries = ref(new Map<string, PersonalRangeRankingEntry>())
  /** The calculator inputs: the answers at the price the list shows (assigned once the price exists below) */
  let inputs = () => needsToInputs(needs.value)
  /** The first reply is in: from then on a row without figures has none, it is not just loading */
  const needsReady = ref(false)
  let requestId = 0
  async function loadRanking() {
    const id = ++requestId
    try {
      const list = await personalRangeService.ranking(inputs())
      if (id === requestId) entries.value = new Map(list.map(e => [`${e.brand}/${e.model}`, e]))
    } catch {
      if (id === requestId) entries.value = new Map()
    }
    if (id === requestId) needsReady.value = true
  }
  let timer: ReturnType<typeof setTimeout> | undefined

  const storedCost = readStoredCost()
  const cost = ref<CostAssumptions>(storedCost?.cost ?? { ...COST_DEFAULTS })
  watch(cost, v => {
    try { localStorage.setItem(COST_STORAGE_KEY, JSON.stringify(v)) } catch { /* private mode */ }
  }, { deep: true })
  /** Combustion comparison only in metric markets (set by the view from the market route). */
  const combustionMarket = ref(true)

  /**
   * First visit: start at the community averages instead of the editorial defaults (a home
   * price carried over from the classic list is kept). The fuel price is only known for
   * Germany; there it is also filled in later when it is still missing, elsewhere it stays
   * empty until typed in.
   */
  function applyReferencePrices(p: ChargingReferencePrices | null | undefined, germanMarket: boolean) {
    if (!p) return
    const apiFuel = germanMarket && p.petrolPricePerLiter != null && p.petrolPricePerLiter > 0 ? round2(p.petrolPricePerLiter) : null
    if (storedCost?.seeded) {
      if (cost.value.fuelPricePerLiter === null && apiFuel !== null) cost.value = { ...cost.value, fuelPricePerLiter: apiFuel }
      return
    }
    cost.value = {
      ...cost.value,
      homePricePerKwh: storedCost === null && p.homePricePerKwh > 0 ? round3(p.homePricePerKwh) : cost.value.homePricePerKwh,
      publicPricePerKwh: p.publicPricePerKwh > 0 ? round3(p.publicPricePerKwh) : cost.value.publicPricePerKwh,
      fuelPricePerLiter: apiFuel,
    }
  }
  function resetCost() {
    cost.value = { ...COST_DEFAULTS }
  }
  // No answer yet on home charging prices like home charging, the common case
  const homeShare = computed(() => effectiveHomeShare(cost.value.homeShare, needs.value.homeCharging !== false))
  /** Commute km a year the yearly savings rest on; null until km a day and days are answered */
  const commuteKmPerYear = computed(() => {
    const weekly = commuteKmPerWeek(needs.value)
    return weekly != null ? weekly * COMMUTE_WEEKS_PER_YEAR : null
  })
  /** The reader's own price from the price slider after the gate; null = the assumptions below */
  const ownPrice = ref<number | null>(null)
  /** One electricity price in EUR/kWh for the answers, the top 5 and the row costs */
  const price = computed(() => ownPrice.value ?? mixedPricePerKwh(cost.value, homeShare.value))
  inputs = () => needsToInputs(needs.value, round3(price.value))
  watch(() => JSON.stringify(inputs()), () => {
    clearTimeout(timer)
    timer = setTimeout(loadRanking, RANKING_DEBOUNCE_MS)
  })
  void loadRanking()
  const combustion = computed(() => combustionMarket.value ? cost.value : { ...cost.value, fuelPricePerLiter: null })

  const enriched = computed(() => models.value.map(m => {
    const costPer100kmEur = m.avgConsumptionKwhPer100km != null ? m.avgConsumptionKwhPer100km * price.value : null
    const savingsPer100kmEur = costPer100kmEur != null ? savingsPer100km(costPer100kmEur, combustion.value, m.category) : null
    const entry = entries.value.get(`${m.brand}/${m.model}`)
    const assessment = entry ? assessEntry(entry, needs.value) : null
    return {
      key: modelKey(m),
      model: m,
      wltpKwhPer100km: wltpReference(m),
      wltpDeviationPct: wltpDeviation(m),
      winterSurchargePct: winterSurcharge(m),
      costPer100kmEur,
      savingsPer100kmEur,
      savingsPerYearEur: savingsPer100kmEur != null && commuteKmPerYear.value != null ? savingsPerYear(savingsPer100kmEur, commuteKmPerYear.value) : null,
      monthlyCostEur: assessment?.monthlyCostEur ?? null,
      monthlyKm: assessment?.monthlyCostEur != null ? entry!.monthlyKm : null,
      combustionLitersPer100km: savingsPer100kmEur != null ? combustionLiters(combustion.value, m.category) : null,
      singleDriver: m.contributorCount === 1,
      needs: assessment,
      ladder: entry ? { zeroStopKm: entry.zeroStopReachKm, oneStopKm: entry.oneStopReachKm, twoStopsKm: entry.twoStopsReachKm } : null,
    }
  }))
  /** The server's figures for every model of the list, in the reply's order; the card's spans and top 5 rest on them */
  const rankingEntries = computed(() => [...entries.value.values()])
  const needsSummary = computed<NeedsSummary>(() => summarizeNeeds(enriched.value.map(r => r.needs)))

  /** "Schafft meine 600 km mit 1 Stopp": the fewest stops over all models, null without a trip */
  const fitStops = computed(() => fewestTripStops(enriched.value.map(r => r.needs)))

  const ranked = computed<RankedModel[]>(() => {
    const valueOf = SORT_VALUE[sort.value]
    const direction = DESCENDING.includes(sort.value) ? -1 : 1
    return enriched.value
      .filter(r => category.value === null || r.model.category === category.value)
      .filter(r => matchesSearch(r.model, query.value))
      .filter(r => !tripOnly.value || fitStops.value == null || meetsNeeds(r.needs, fitStops.value))
      .sort((a, b) => {
        const va = valueOf(a)
        const vb = valueOf(b)
        if (va == null && vb != null) return 1
        if (vb == null && va != null) return -1
        if (va != null && vb != null && va !== vb) return (va - vb) * direction
        return b.model.logCount - a.model.logCount
          || a.model.modelDisplayName.localeCompare(b.model.modelDisplayName)
      })
      .map((r, i) => ({ ...r, rank: i + 1 }))
  })

  /** Models without driver data, under the same class filter and search as the ranking. */
  const withoutData = computed(() => modelsWithoutData.value
    .filter(m => category.value === null || m.category === category.value)
    .filter(m => matchesSearch(m, query.value)))
  const searchHitsOnlyWithoutData = computed(() => query.value.trim() !== '' && ranked.value.length === 0 && withoutData.value.length > 0)

  const avgWltpDeviationPct = computed<number | null>(() => {
    const deviations = enriched.value.map(r => r.wltpDeviationPct).filter((v): v is number => v != null)
    return deviations.length ? deviations.reduce((s, v) => s + v, 0) / deviations.length : null
  })

  const compareKeys = ref<string[]>([])
  const canAddCompare = computed(() => compareKeys.value.length < MAX_COMPARE)
  const isInCompare = (key: string) => compareKeys.value.includes(key)
  function toggleCompare(key: string) {
    if (isInCompare(key)) compareKeys.value = compareKeys.value.filter(k => k !== key)
    else if (canAddCompare.value) compareKeys.value = [...compareKeys.value, key]
  }
  function clearCompare() {
    compareKeys.value = []
  }

  return {
    sort,
    category,
    query,
    price,
    ownPrice,
    cost,
    homeShare,
    combustionMarket,
    applyReferencePrices,
    resetCost,
    needs,
    needsSummary,
    needsReady,
    rankingEntries,
    tripOnly,
    fitStops,
    ranked,
    withoutData,
    searchHitsOnlyWithoutData,
    avgWltpDeviationPct,
    compareKeys,
    canAddCompare,
    isInCompare,
    toggleCompare,
    clearCompare,
  }
}
