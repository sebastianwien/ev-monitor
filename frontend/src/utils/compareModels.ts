import type { RankedModel } from '../composables/useModelRanking'
import type { WltpVariant } from '../api/publicModelService'
import { rangeKm } from './modelStory'
import { consumptionDeltaPercent } from './unitConversions'

/**
 * Pure helpers of the compare page. The figures themselves come from the model ranking
 * (useModelRanking), the page only marks and words them.
 */

/**
 * Which models are better on a figure: those sharing the best value while at least one model is
 * behind. A tie marks nothing, a missing value is left out. Pass the values as the page shows
 * them (rounded), so two figures that read alike never count as different.
 */
export function betterMarks(values: (number | null)[], lowerIsBetter: boolean): boolean[] {
  const present = values.filter((v): v is number => v != null)
  if (present.length < 2) return values.map(() => false)
  const best = lowerIsBetter ? Math.min(...present) : Math.max(...present)
  if (present.every(v => v === best)) return values.map(() => false)
  return values.map(v => v === best)
}

/** The compared models as one Plausible property: keys sorted by code point (same in every browser) and joined, so A,B and B,A are one set. */
export function compareSetKey(keys: string[]): string {
  return [...keys].sort().join(',')
}

export interface SummaryInput { name: string; consumption: number | null; winterKm: number | null }
export interface CompareSummary {
  /** the most frugal model against the runner-up; null below 1 % */
  lean: { winner: string; other: string; pct: number } | null
  /** the model that goes furthest in winter against the runner-up, in 10 km steps; null below 10 km */
  winter: { winner: string; other: string; km: number } | null
}

/** One result sentence for the page head, from the figures the table shows; no superlatives, only differences. */
export function compareSummary(models: SummaryInput[]): CompareSummary {
  const lean = [...models].filter(m => m.consumption != null).sort((a, b) => a.consumption! - b.consumption!)
  const pct = lean.length >= 2 ? Math.round((lean[1].consumption! - lean[0].consumption!) / lean[1].consumption! * 100) : 0
  const winter = [...models].filter(m => m.winterKm != null).sort((a, b) => b.winterKm! - a.winterKm!)
  const km = winter.length >= 2 ? Math.round((winter[0].winterKm! - winter[1].winterKm!) / 10) * 10 : 0
  return {
    lean: pct >= 1 ? { winner: lean[0].name, other: lean[1].name, pct } : null,
    winter: km >= 10 ? { winner: winter[0].name, other: winter[1].name, km } : null,
  }
}

/** The variants worth choosing: only those the drivers have driven. */
export function variantsWithTrips(variants: WltpVariant[]): WltpVariant[] {
  return variants.filter(v => (v.realConsumptionTripCount ?? 0) > 0)
}

/**
 * A ranked model on one chosen battery, by the model page's rule: consumption stays the drivers'
 * mean, range and winter range follow the battery (rangeKm), WLTP and battery the variant. The
 * motorway ladder, commute and trip were worked out by the server on the largest battery, so a
 * smaller battery drops them; the monthly cost does not depend on the battery and stays.
 */
export function applyVariant(item: RankedModel, variant: WltpVariant): RankedModel {
  const kwh = variant.batteryCapacityKwh
  const m = item.model
  const range = rangeKm(kwh, m.avgConsumptionKwhPer100km)
  const winterRange = rangeKm(kwh, m.winterConsumptionKwhPer100km ?? null)
  const wltp = variant.wltpConsumptionKwhPer100km ?? item.wltpKwhPer100km
  const largest = m.maxNetCapacityKwh == null || kwh >= m.maxNetCapacityKwh - 0.5
  return {
    ...item,
    model: {
      ...m,
      minNetCapacityKwh: kwh,
      maxNetCapacityKwh: kwh,
      typicalRangeMinKm: range,
      typicalRangeMaxKm: range,
      winterRangeMinKm: winterRange,
      winterRangeMaxKm: winterRange,
    },
    wltpKwhPer100km: wltp,
    wltpDeviationPct: wltp != null && wltp > 0 && m.avgConsumptionKwhPer100km != null ? consumptionDeltaPercent(m.avgConsumptionKwhPer100km, wltp) : null,
    ladder: largest ? item.ladder : null,
    needs: largest || !item.needs ? item.needs : { ...item.needs, commute: null, trip: null },
  }
}
