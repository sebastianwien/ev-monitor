import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useLocaleFormat } from './useLocaleFormat'
import { convertCostPerDistance } from '../utils/unitConversions'
import type { RankedModel } from './useModelRanking'
import type { FuelKind } from '../utils/costMix'

/** Ranges read as rough figures: whole tens, in the display unit */
const RANGE_STEP = 10

/**
 * Wording a ranked model shares between its row and its open detail: range span, trip, fuel,
 * data basis and the WLTP gap. Getters keep it reactive on the component's props.
 */
export function useRankedModelText(item: () => RankedModel, longestTripKm: () => number | null, fuel: () => FuelKind) {
  const { t } = useI18n()
  const { formatNumber, formatDecimal, formatDistance, convertDistance, distanceUnitLabel, formatCostPerDistance, unitSystem } = useLocaleFormat()

  const m = computed(() => item().model)

  const fmtKm = (km: number) => formatDistance(km, { showUnit: false, step: RANGE_STEP })
  const fmtKmUnit = (km: number) => formatDistance(km, { step: RANGE_STEP })

  /** "8 h 5 min", "45 min", "9 h", as on the model page */
  function duration(minutes: number): string {
    const h = Math.floor(minutes / 60)
    const min = minutes % 60
    if (h === 0) return t('personal_range.duration_m', { m: min })
    return min === 0 ? t('personal_range.duration_h', { h }) : t('personal_range.duration_h_m', { h, m: min })
  }

  /** The reader's own trip in the market's unit, named wherever the trip is; null until answered */
  const tripParams = computed(() => {
    const km = longestTripKm()
    return km != null ? { trip: formatNumber(Math.round(convertDistance(km))), unit: distanceUnitLabel() } : null
  })

  const fuelLabel = computed(() => t(`models_ranking.assumptions.${fuel()}`))
  const costLabel = computed(() => item().costPer100kmEur != null ? formatCostPerDistance(item().costPer100kmEur!) : '–')
  // Absolute cost of the assumed combustion car per 100 km: EV cost plus savings
  const combustionCostEur = computed(() => {
    const ev = item().costPer100kmEur
    const s = item().savingsPer100kmEur
    return ev != null && s != null ? ev + s : null
  })
  const combustionCost = computed(() => combustionCostEur.value != null
    ? formatDecimal(convertCostPerDistance(combustionCostEur.value, unitSystem.value), 2)
    : '')

  // Range: the typical span from the smallest to the largest battery (100 to 10 % at the drivers'
  // mean), the best variant's own real range as fallback
  const typicalSpan = computed(() => {
    const lo = m.value.typicalRangeMinKm
    const hi = m.value.typicalRangeMaxKm
    return lo != null && hi != null ? { lo, hi } : null
  })
  const hasBatterySpan = computed(() => !!typicalSpan.value && typicalSpan.value.lo !== typicalSpan.value.hi)
  const rangeValue = computed(() => {
    const span = typicalSpan.value
    if (span) return hasBatterySpan.value ? t('models_ranking.row.span', { min: fmtKm(span.lo), max: fmtKm(span.hi) }) : fmtKm(span.hi)
    return m.value.realRangeKm != null ? fmtKm(m.value.realRangeKm) : '–'
  })

  // Data basis: "412 Ladevorgänge, 9 Fahrer". Counts missing on an older backend → sessions only.
  const driverCount = computed(() => m.value.contributorCount ?? null)
  const driversLabel = computed(() => driverCount.value != null
    ? t('models_ranking.row.drivers_plural', { count: formatNumber(driverCount.value) }, driverCount.value)
    : '')

  function signedPct(v: number, decimals = 0): string {
    return `${v > 0 ? '+' : ''}${formatDecimal(v, decimals)} %`
  }
  /** Deviation as the reader sees it: in mi/kWh more is better, so the sign flips. */
  const displayWltpDeviation = computed(() => {
    const real = m.value.avgConsumptionKwhPer100km
    const wltp = item().wltpKwhPer100km
    if (real == null || wltp == null || real <= 0 || wltp <= 0) return null
    return unitSystem.value.consumptionInverse ? ((wltp - real) / real) * 100 : item().wltpDeviationPct
  })

  return {
    m, fmtKm, fmtKmUnit, duration, tripParams, fuelLabel, costLabel, combustionCostEur, combustionCost,
    typicalSpan, hasBatterySpan, rangeValue, driverCount, driversLabel, signedPct, displayWltpDeviation,
  }
}
