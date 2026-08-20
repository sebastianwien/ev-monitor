/**
 * Charging efficiency estimation for cost display.
 *
 * When a log has only kwhAtVehicle (netto, from the car API), we cannot know what the
 * wallbox actually billed at the grid - that delta is the AC-charging-loss. To give
 * users a realistic "what did this charging session likely cost on the electricity bill"
 * hint, we estimate the efficiency from the user's own logs that DO have both brutto
 * (kwhCharged) and netto (kwhAtVehicle) measured. The median over all qualifying samples
 * is used (no cap) - the median is robust against outliers, and showing the real sample
 * count in the hint keeps the user informed about the data basis.
 *
 * Falls back to a system-wide pauschale (AC 0.90, DC 0.95) if no samples are available.
 *
 * The function is intentionally pure (no Vue reactivity) so it can be reused in any
 * component context and unit-tested without mounting a Vue tree.
 */

export const AC_PAUSCHALE = 0.90
export const DC_PAUSCHALE = 0.95

export type EfficiencySource = 'measured-median' | 'pauschale'

export interface ChargingEfficiencyResult {
  /** Efficiency factor in [0, 1] - multiply netto by 1/value to estimate brutto. */
  value: number
  /** Where the value comes from - drives the wording of the user-facing hint. */
  source: EfficiencySource
  /** Number of measured samples used (0 when source = 'pauschale'). */
  sampleSize: number
}

interface EfficiencyInput {
  kwhCharged: number | null
  kwhAtVehicle: number | null
  chargingType: 'AC' | 'DC' | 'UNKNOWN' | null
  chargingProviderId: string | null
  loggedAt: string
}

export interface ChargingEfficiencyOptions {
  chargingType: 'AC' | 'DC'
  /** When provided, only logs at this tariff are considered. */
  chargingProviderId?: string | null
}

export function computeChargingEfficiency<T extends EfficiencyInput>(
  logs: T[],
  opts: ChargingEfficiencyOptions,
): ChargingEfficiencyResult {
  const pauschale = opts.chargingType === 'DC' ? DC_PAUSCHALE : AC_PAUSCHALE

  const candidates = logs.filter((l) => {
    if (l.chargingType !== opts.chargingType) return false
    if (l.kwhCharged == null || l.kwhCharged <= 0) return false
    if (l.kwhAtVehicle == null || l.kwhAtVehicle <= 0) return false
    if (opts.chargingProviderId != null && l.chargingProviderId !== opts.chargingProviderId) return false
    return true
  })

  if (candidates.length === 0) {
    return { value: pauschale, source: 'pauschale', sampleSize: 0 }
  }

  const samples = candidates
    .map((l) => (l.kwhAtVehicle as number) / (l.kwhCharged as number))
    .sort((a, b) => a - b)

  const median = samples.length % 2 === 1
    ? samples[Math.floor(samples.length / 2)]
    : (samples[samples.length / 2 - 1] + samples[samples.length / 2]) / 2

  return { value: median, source: 'measured-median', sampleSize: samples.length }
}

/**
 * Estimate the grid-side ("brutto") kWh from a netto-only log using either the user's
 * measured efficiency or the system pauschale. Returns null if no estimation is possible.
 */
export function estimateBruttoKwh(nettoKwh: number, efficiency: ChargingEfficiencyResult): number {
  return nettoKwh / efficiency.value
}

/**
 * A log carries netto-only cost when it has cost_eur and kwhAtVehicle set, but no
 * kwhCharged (= no wallbox brutto reading). In that case the stored cost_eur was computed
 * as tariff x netto and the user's real electricity bill includes additional AC losses.
 */
export interface CostHintLog {
  costEur: number | null
  kwhCharged: number | null
  kwhAtVehicle: number | null
  chargingType: 'AC' | 'DC' | 'UNKNOWN' | null
  chargingProviderId: string | null
}

export function isNettoOnlyCostLog(log: CostHintLog | null | undefined): boolean {
  if (log == null) return false
  if (log.costEur == null) return false
  if (log.kwhCharged != null && log.kwhCharged > 0) return false
  if (log.kwhAtVehicle == null || log.kwhAtVehicle <= 0) return false
  return log.chargingType === 'AC' || log.chargingType === 'DC'
}

/**
 * Energy basis the stored cost_eur was computed on - mirrors backend EvLog.costBasisKwh():
 * brutto (kwhCharged) when measured, else netto (kwhAtVehicle). This is the ONLY correct
 * divisor for a per-kWh price, because cost = thisBasis x tariff. Dividing cost by a
 * different basis (e.g. netto when cost was billed on brutto) inflates the per-kWh price.
 * Returns null when neither field is positive.
 */
export function costBasisKwh(log: { kwhCharged: number | null; kwhAtVehicle: number | null }): number | null {
  if (log.kwhCharged != null && log.kwhCharged > 0) return log.kwhCharged
  if (log.kwhAtVehicle != null && log.kwhAtVehicle > 0) return log.kwhAtVehicle
  return null
}

export interface GroupCostResult {
  /** Sum of cost_eur over sub-logs that have both a cost and an energy basis (null if none). */
  totalCostEur: number | null
  /**
   * Sum of costBasisKwh over those same sub-logs - the correct DIVISOR for the per-kWh
   * price, not the price itself. Named ...KwhTotal so it cannot be mistaken for EUR/kWh.
   */
  costBasisKwhTotal: number
  /**
   * True when at least one cost-carrying sub-log is netto-only: the header total then
   * understates the real grid cost, so the cost chip is rendered dashed (same signal as
   * a single netto-only log). The displayed price stays the truthful tariff - no pauschale
   * is applied here; the grossed-up "real cost" estimate lives only in the per-log hint.
   */
  costIsNettoOnly: boolean
}

/**
 * Aggregate the per-kWh cost basis for a Ladegruppe so the header price stays consistent
 * with the single-log chip and the tariff the user actually set. Cost and energy are summed
 * on the SAME basis each sub-log's cost was computed on (costBasisKwh), and sub-logs without
 * a cost or without any energy reading are excluded so they cannot distort the rate.
 */
export function aggregateGroupCost<T extends CostHintLog>(subs: T[]): GroupCostResult {
  const withCost = subs.filter((l) => l.costEur != null && costBasisKwh(l) != null)
  if (withCost.length === 0) return { totalCostEur: null, costBasisKwhTotal: 0, costIsNettoOnly: false }
  const totalCostEur = withCost.reduce((s, l) => s + (l.costEur as number), 0)
  const costBasisKwhTotal = withCost.reduce((s, l) => s + (costBasisKwh(l) as number), 0)
  const costIsNettoOnly = withCost.some((l) => isNettoOnlyCostLog(l))
  return { totalCostEur, costBasisKwhTotal, costIsNettoOnly }
}

export interface RealCostHint {
  /** Total real cost estimate in EUR (= netto cost grossed up by AC-loss factor). */
  bruttoCostEur: number
  /** Estimated grid-side kWh (= netto / efficiency). */
  bruttoKwh: number
  /**
   * Effective EUR per netto kWh including AC losses (= bruttoCostEur / nettoKwh).
   * Used when the cost pill is in per-kWh mode so the hint stays comparable to the pill.
   * Equals (pill ct/kWh) / efficiency.
   */
  bruttoRatePerNettoKwhEur: number
  efficiencyPercent: number
  source: EfficiencySource
  sampleSize: number
}

/**
 * Build the "≈ X € real" hint for a netto-only log, using the user's measured efficiency
 * if available (median over recent same-tariff logs), else the pauschale. Returns null
 * when the log doesn't qualify for a real-cost hint.
 *
 * The brutto cost is derived purely from the displayed netto cost and the efficiency factor -
 * it is NOT persisted. The stored cost_eur stays the (truthful) netto x tariff.
 */
export function computeRealCostHint<T extends CostHintLog & EfficiencyInput>(
  log: T,
  allLogs: T[],
): RealCostHint | null {
  if (!isNettoOnlyCostLog(log)) return null
  const chargingType = log.chargingType as 'AC' | 'DC'
  const eff = computeChargingEfficiency(allLogs, {
    chargingType,
    chargingProviderId: log.chargingProviderId,
  })
  const nettoKwh = log.kwhAtVehicle as number
  const nettoCost = log.costEur as number
  const bruttoKwh = nettoKwh / eff.value
  const bruttoCostEur = nettoCost * (bruttoKwh / nettoKwh)
  return {
    bruttoCostEur,
    bruttoKwh,
    bruttoRatePerNettoKwhEur: bruttoCostEur / nettoKwh,
    efficiencyPercent: Math.round(eff.value * 1000) / 10,
    source: eff.source,
    sampleSize: eff.sampleSize,
  }
}
