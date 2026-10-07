import { computed, ref, watch, type Ref } from 'vue'
import type { TopModelPreview } from '../api/publicModelService'
import { consumptionDeltaPercent } from '../utils/unitConversions'

export type RankingSort = 'efficient' | 'range' | 'wltp' | 'winter' | 'data'

export const RANKING_SORTS: RankingSort[] = ['efficient', 'range', 'wltp', 'winter', 'data']
export const MAX_COMPARE = 3
/** Same key as the classic model list, so a chosen price carries over between both views. */
export const PRICE_STORAGE_KEY = 'ev-price-per-kwh'
export const PRICE_MIN = 0.10
export const PRICE_MAX = 0.90
const DEFAULT_PRICE = 0.30

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
}

export function modelKey(m: TopModelPreview): string {
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
function matchesSearch(m: TopModelPreview, query: string): boolean {
  const q = query.trim().toLowerCase()
  if (!q) return true
  const haystack = `${m.brandDisplayName} ${m.modelDisplayName}`.toLowerCase().replace(/_/g, ' ')
  return q.split(/\s+/).every(term => haystack.includes(term))
}

// Sort value per mode; null always sorts last. Ascending unless listed in DESCENDING.
const SORT_VALUE: Record<RankingSort, (r: Omit<RankedModel, 'rank'>) => number | null> = {
  efficient: r => r.model.avgConsumptionKwhPer100km,
  range: r => r.model.realRangeKm,
  wltp: r => (r.wltpDeviationPct != null ? Math.abs(r.wltpDeviationPct) : null),
  winter: r => r.winterSurchargePct,
  data: r => r.model.logCount,
}
const DESCENDING: RankingSort[] = ['range', 'data']

function readStoredPrice(): number | null {
  try {
    const raw = localStorage.getItem(PRICE_STORAGE_KEY)
    if (raw === null) return null
    const value = Number(raw)
    return Number.isFinite(value) && value > 0 && value <= 1 ? value : null
  } catch {
    return null
  }
}

/**
 * State of the model ranking: sort mode, class filter, search, compare picks and the
 * electricity price for cost per 100 km. Pure UI logic on top of the top-models DTO,
 * no consumption formula of its own.
 */
export function useModelRanking(models: Ref<TopModelPreview[]>) {
  const sort = ref<RankingSort>('efficient')
  const category = ref<string | null>(null)
  const query = ref('')

  const storedPrice = readStoredPrice()
  const price = ref<number>(storedPrice ?? DEFAULT_PRICE)
  watch(price, v => {
    try { localStorage.setItem(PRICE_STORAGE_KEY, String(v)) } catch { /* private mode */ }
  })

  /** First visit: start at the community home-charging price instead of the fixed default. */
  function applyDefaultPrice(homePricePerKwh: number | null | undefined) {
    if (storedPrice !== null || homePricePerKwh == null || homePricePerKwh <= 0) return
    price.value = Math.round(homePricePerKwh * 100) / 100
  }

  const enriched = computed(() => models.value.map(m => ({
    key: modelKey(m),
    model: m,
    wltpKwhPer100km: wltpReference(m),
    wltpDeviationPct: wltpDeviation(m),
    winterSurchargePct: winterSurcharge(m),
    costPer100kmEur: m.avgConsumptionKwhPer100km != null ? m.avgConsumptionKwhPer100km * price.value : null,
  })))

  const ranked = computed<RankedModel[]>(() => {
    const valueOf = SORT_VALUE[sort.value]
    const direction = DESCENDING.includes(sort.value) ? -1 : 1
    return enriched.value
      .filter(r => category.value === null || r.model.category === category.value)
      .filter(r => matchesSearch(r.model, query.value))
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
    applyDefaultPrice,
    ranked,
    avgWltpDeviationPct,
    compareKeys,
    canAddCompare,
    isInCompare,
    toggleCompare,
    clearCompare,
  }
}
