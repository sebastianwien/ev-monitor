import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useLocaleFormat } from './useLocaleFormat'
import { convertCostPerDistance } from '../utils/unitConversions'
import type { RankedModel } from './useModelRanking'
import type { FuelKind } from '../utils/costMix'

/** One line of "Was die Fahrer messen": label and value */
export interface RankedModelFact { key: string; label: string; value: string }

/** Ranges read as rough figures: whole tens, in the display unit */
const RANGE_STEP = 10

/**
 * Wording a ranked model shares between its row and its open detail: range span, trip, fuel,
 * data basis and the WLTP gap. Getters keep it reactive on the component's props.
 */
export function useRankedModelText(item: () => RankedModel, longestTripKm: () => number | null, fuel: () => FuelKind) {
  const { t } = useI18n()
  const {
    formatNumber, formatDecimal, formatDistance, convertDistance, distanceUnitLabel, formatCostPerDistance, unitSystem,
    formatConsumption, consumptionUnitLabel, formatCurrency, formatCostPerKwh,
  } = useLocaleFormat()

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

  // ── The row's cells: consumption, cost per 100 km, range ─────────────────
  const realText = computed(() => formatConsumption(m.value.avgConsumptionKwhPer100km, { showUnit: false }))
  const wltpText = computed(() => formatConsumption(item().wltpKwhPer100km, { showUnit: false }))
  /** "5,93 €": per 100 km of the market's unit, the column head names the distance */
  const electricityCost = computed(() => item().costPer100kmEur != null
    ? formatCurrency(convertCostPerDistance(item().costPer100kmEur!, unitSystem.value))
    : '–')
  /** "Benziner 14,62 €": the class's combustion car per 100 km */
  const combustionCaption = computed(() => combustionCostEur.value != null
    ? t('models_ranking.row.combustion_short', {
        fuel: fuelLabel.value,
        amount: formatCurrency(convertCostPerDistance(combustionCostEur.value, unitSystem.value)),
      })
    : '')
  /** "Fahrer zahlen Ø 29,6 ct/kWh": the drivers' own average next to the list price */
  const driversPrice = computed(() => m.value.avgCostPerKwh != null
    ? t('models_ranking.row.drivers_price', { price: formatCostPerKwh(m.value.avgCostPerKwh) })
    : null)
  /** Motorway from a full battery, largest battery: "Autobahn ohne Stopp bis 310, 1 Stopp bis 550" */
  const ladderLine = computed(() => {
    const l = item().ladder
    return l ? t('models_ranking.row.ladder_line', { zero: fmtKm(l.zeroStopKm), one: fmtKm(l.oneStopKm) }) : ''
  })

  // ── What the drivers measure beyond the row ──────────────────────────────
  /** Only what the closed row leaves out (it shows average consumption, WLTP, range and charging price);
   *  each fact only with a value, the data basis always. */
  const facts = computed<RankedModelFact[]>(() => {
    const x = m.value
    const out: RankedModelFact[] = []
    const add = (key: string, value: string | null) => { if (value) out.push({ key, label: t(`models_ranking.detail.fact_${key}`), value }) }

    const summer = x.summerConsumptionKwhPer100km
    const winter = x.winterConsumptionKwhPer100km
    add('consumption', summer != null && winter != null
      ? t('models_ranking.detail.fact_seasons', { summer: formatConsumption(summer, { showUnit: false }), winter: formatConsumption(winter) })
      : null)

    const lo = x.winterRangeMinKm
    const hi = x.winterRangeMaxKm
    add('winter', [
      ...(item().winterSurchargePct != null ? [t('models_ranking.detail.fact_winter_surcharge', { pct: signedPct(item().winterSurchargePct!) })] : []),
      ...(lo != null && hi != null ? [`${fmtKm(lo) === fmtKm(hi) ? fmtKm(hi) : t('models_ranking.row.span', { min: fmtKm(lo), max: fmtKm(hi) })} ${distanceUnitLabel()}`] : []),
    ].join(' · ') || null)

    add('highway', item().ladder ? t('models_ranking.detail.fact_ladder', { value: fmtKmUnit(item().ladder!.twoStopsKm) }) : null)

    // consumption against WLTP, in kWh space: "mehr" means more energy than the rating
    const gap = item().wltpDeviationPct
    add('wltp', gap != null && Math.round(gap) !== 0
      ? t(gap > 0 ? 'models_ranking.detail.fact_wltp_above' : 'models_ranking.detail.fact_wltp_below', { pct: `${formatDecimal(Math.abs(gap), 0)} %` })
      : null)

    const capLo = x.minNetCapacityKwh
    const capHi = x.maxNetCapacityKwh
    add('battery', capLo != null && capHi != null
      ? (Math.round(capLo) === Math.round(capHi)
          ? t('models_ranking.detail.fact_battery_value', { value: formatDecimal(capHi, 0) })
          : t('models_ranking.detail.fact_battery_span', { min: formatDecimal(capLo, 0), max: formatDecimal(capHi, 0) }))
      : null)

    const logs = t('models_ranking.row.logs_plural', { count: formatNumber(x.logCount) }, x.logCount)
    add('data', x.carCount != null
      ? t('models_ranking.detail.measured_data', { logs, cars: t('models_ranking.detail.cars_plural', { count: formatNumber(x.carCount) }, x.carCount) })
      : logs)
    return out
  })

  return {
    m, fmtKm, fmtKmUnit, duration, tripParams, fuelLabel, costLabel, combustionCostEur, combustionCost,
    typicalSpan, hasBatterySpan, rangeValue, driverCount, driversLabel, signedPct, displayWltpDeviation,
    realText, wltpText, electricityCost, combustionCaption, driversPrice, ladderLine, facts, consumptionUnitLabel,
  }
}
