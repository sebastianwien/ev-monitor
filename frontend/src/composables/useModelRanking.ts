import { computed, ref, watch, type Ref } from 'vue'
import type { ChargingReferencePrices, ModelWithoutData, TopModelPreview } from '../api/publicModelService'
import { consumptionDeltaPercent } from '../utils/unitConversions'
import { assessModel, summarizeNeeds, NEEDS_DEFAULTS, type NeedsAssessment, type NeedsInput, type NeedsSummary } from '../utils/needsCheck'
import {
  COST_DEFAULTS, PRICE_MAX, effectiveHomeShare, mixedPricePerKwh, savingsPer100km, savingsPerYear, isCostAssumptions,
  type CostAssumptions,
} from '../utils/costMix'

export type RankingSort = 'efficient' | 'range' | 'wltp' | 'winter' | 'data'

export const RANKING_SORTS: RankingSort[] = ['efficient', 'range', 'wltp', 'winter', 'data']
export const MAX_COMPARE = 3
/** Key of the classic model list; read once as the initial home price, never written here. */
export const PRICE_STORAGE_KEY = 'ev-price-per-kwh'
/** Cost assumptions stay in the browser only, nothing is sent to the server. */
export const COST_STORAGE_KEY = 'ev-cost-assumptions'
export const PRIORITY_STORAGE_KEY = 'ev-ranking-priority'

/** "What matters most to you?" One answer, mapped to a sort. */
export type Priority = 'cost' | 'range' | 'winter' | 'wltp' | 'data'
export const PRIORITIES: Priority[] = ['cost', 'range', 'winter', 'wltp', 'data']
const PRIORITY_SORT: Record<Priority, RankingSort> = { cost: 'efficient', range: 'range', winter: 'winter', wltp: 'wltp', data: 'data' }
/** Needs-check inputs stay in the browser only, nothing is sent to the server. */
export const NEEDS_STORAGE_KEY = 'ev-needs-check'
export const NEEDS_KM_MAX = 5000

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
  savingsPerYearEur: number | null
  /** All values of this model come from one driver */
  singleDriver: boolean
  /** Charging interval, stops and longest trip for the current needs-check inputs */
  needs: NeedsAssessment
}

export function modelKey(m: Pick<TopModelPreview, 'brandDisplayName' | 'modelUrlSlug'>): string {
  return `${m.brandDisplayName}/${m.modelUrlSlug}`
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

function isNeedsInput(v: unknown): v is NeedsInput {
  if (!v || typeof v !== 'object') return false
  const o = v as Record<string, unknown>
  const km = (x: unknown) => typeof x === 'number' && Number.isFinite(x) && x >= 0 && x <= NEEDS_KM_MAX
  return km(o.dailyKm) && km(o.longestTripKm) && typeof o.homeCharging === 'boolean'
}

function readStoredNeeds(): NeedsInput | null {
  try {
    const raw = localStorage.getItem(NEEDS_STORAGE_KEY)
    if (raw === null) return null
    const parsed: unknown = JSON.parse(raw)
    return isNeedsInput(parsed) ? parsed : null
  } catch {
    return null
  }
}

function readStoredCost(): CostAssumptions | null {
  try {
    const raw = localStorage.getItem(COST_STORAGE_KEY)
    if (raw !== null) {
      const parsed: unknown = JSON.parse(raw)
      return isCostAssumptions(parsed) ? parsed : null
    }
    // Classic list price from an earlier visit: take it as the home price
    const legacy = Number(localStorage.getItem(PRICE_STORAGE_KEY))
    if (Number.isFinite(legacy) && legacy > 0 && legacy <= PRICE_MAX) {
      return { ...COST_DEFAULTS, homePricePerKwh: legacy }
    }
    return null
  } catch {
    return null
  }
}

function readStoredPriority(): Priority | null {
  try {
    const raw = localStorage.getItem(PRIORITY_STORAGE_KEY)
    return (PRIORITIES as string[]).includes(raw ?? '') ? raw as Priority : null
  } catch {
    return null
  }
}

const round2 = (v: number) => Math.round(v * 100) / 100

/**
 * State of the model ranking: sort mode, class filter, search, compare picks, needs check and
 * cost assumptions (blended electricity price, combustion comparison). Pure UI logic on top of
 * the top-models DTO, no consumption formula of its own.
 */
export function useModelRanking(models: Ref<TopModelPreview[]>, modelsWithoutData: Ref<ModelWithoutData[]> = ref([])) {
  const sort = ref<RankingSort>('efficient')
  const priority = ref<Priority | null>(readStoredPriority())
  if (priority.value) sort.value = PRIORITY_SORT[priority.value]
  watch(priority, p => {
    if (p) sort.value = PRIORITY_SORT[p]
    try {
      if (p) localStorage.setItem(PRIORITY_STORAGE_KEY, p)
      else localStorage.removeItem(PRIORITY_STORAGE_KEY)
    } catch { /* private mode */ }
  }, { flush: 'sync' })
  // A chip click that leaves the priority's sort is a new decision: the answer no longer applies
  watch(sort, s => {
    if (priority.value && PRIORITY_SORT[priority.value] !== s) priority.value = null
  }, { flush: 'sync' })
  const category = ref<string | null>(null)
  const query = ref('')
  /** Only models whose largest battery makes the longest trip without a stop */
  const tripOnly = ref(false)

  const needs = ref<NeedsInput>(readStoredNeeds() ?? { ...NEEDS_DEFAULTS })
  watch(needs, v => {
    try { localStorage.setItem(NEEDS_STORAGE_KEY, JSON.stringify(v)) } catch { /* private mode */ }
  }, { deep: true })
  const needsSummary = computed<NeedsSummary>(() => summarizeNeeds(models.value, needs.value))

  const storedCost = readStoredCost()
  const cost = ref<CostAssumptions>(storedCost ?? { ...COST_DEFAULTS })
  watch(cost, v => {
    try { localStorage.setItem(COST_STORAGE_KEY, JSON.stringify(v)) } catch { /* private mode */ }
  }, { deep: true })
  /** Combustion comparison only in metric markets (set by the view from the market route). */
  const combustionMarket = ref(true)

  /**
   * First visit: start at the community averages instead of the editorial defaults.
   * The fuel price is only known for Germany; elsewhere it stays empty until typed in.
   */
  function applyReferencePrices(p: ChargingReferencePrices | null | undefined, germanMarket: boolean) {
    if (storedCost !== null || !p) return
    cost.value = {
      ...cost.value,
      homePricePerKwh: p.homePricePerKwh > 0 ? round2(p.homePricePerKwh) : cost.value.homePricePerKwh,
      publicPricePerKwh: p.publicPricePerKwh > 0 ? round2(p.publicPricePerKwh) : cost.value.publicPricePerKwh,
      litersPer100km: p.combustionLitersPer100km ?? cost.value.litersPer100km,
      fuelPricePerLiter: germanMarket && p.petrolPricePerLiter != null && p.petrolPricePerLiter > 0 ? round2(p.petrolPricePerLiter) : null,
    }
  }
  function resetCost() {
    cost.value = { ...COST_DEFAULTS }
  }
  const homeShare = computed(() => effectiveHomeShare(cost.value.homeShare, needs.value.homeCharging))
  /** Blended electricity price in EUR/kWh the row costs are based on */
  const price = computed(() => mixedPricePerKwh(cost.value, homeShare.value))
  const combustion = computed(() => combustionMarket.value ? cost.value : { ...cost.value, fuelPricePerLiter: null })

  const enriched = computed(() => models.value.map(m => {
    const costPer100kmEur = m.avgConsumptionKwhPer100km != null ? m.avgConsumptionKwhPer100km * price.value : null
    const savingsPer100kmEur = costPer100kmEur != null ? savingsPer100km(costPer100kmEur, combustion.value) : null
    return {
      key: modelKey(m),
      model: m,
      wltpKwhPer100km: wltpReference(m),
      wltpDeviationPct: wltpDeviation(m),
      winterSurchargePct: winterSurcharge(m),
      costPer100kmEur,
      savingsPer100kmEur,
      savingsPerYearEur: savingsPer100kmEur != null ? savingsPerYear(savingsPer100kmEur, needs.value.dailyKm) : null,
      singleDriver: m.contributorCount === 1,
      needs: assessModel(m, needs.value),
    }
  }))

  const ranked = computed<RankedModel[]>(() => {
    const valueOf = SORT_VALUE[sort.value]
    const direction = DESCENDING.includes(sort.value) ? -1 : 1
    return enriched.value
      .filter(r => category.value === null || r.model.category === category.value)
      .filter(r => matchesSearch(r.model, query.value))
      .filter(r => !tripOnly.value || (r.needs.assessable && r.needs.tripStops.min === 0))
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
    cost,
    homeShare,
    combustionMarket,
    applyReferencePrices,
    resetCost,
    priority,
    needs,
    needsSummary,
    tripOnly,
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
