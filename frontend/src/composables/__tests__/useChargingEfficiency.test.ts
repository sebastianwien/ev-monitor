import { describe, it, expect } from 'vitest'
import {
  computeChargingEfficiency,
  computeRealCostHint,
  isNettoOnlyCostLog,
  costBasisKwh,
  aggregateGroupCost,
} from '../useChargingEfficiency'

// Minimal log-shape we operate on - just the fields the function actually reads.
type Log = {
  kwhCharged: number | null
  kwhAtVehicle: number | null
  chargingType: 'AC' | 'DC' | 'UNKNOWN' | null
  chargingProviderId: string | null
  loggedAt: string
}

const log = (over: Partial<Log>): Log => ({
  kwhCharged: null,
  kwhAtVehicle: null,
  chargingType: 'AC',
  chargingProviderId: null,
  loggedAt: '2026-05-01T12:00:00',
  ...over,
})

describe('computeChargingEfficiency', () => {
  it('returns AC pauschale (0.90) when no logs have both brutto and netto', () => {
    const logs: Log[] = [
      log({ kwhAtVehicle: 13.83, kwhCharged: null }),
      log({ kwhAtVehicle: 10.76, kwhCharged: null }),
    ]
    const result = computeChargingEfficiency(logs, { chargingType: 'AC' })
    expect(result.value).toBeCloseTo(0.9, 4)
    expect(result.source).toBe('pauschale')
    expect(result.sampleSize).toBe(0)
  })

  it('returns DC pauschale (0.95) for DC fast charging without samples', () => {
    const result = computeChargingEfficiency([], { chargingType: 'DC' })
    expect(result.value).toBeCloseTo(0.95, 4)
    expect(result.source).toBe('pauschale')
  })

  it('computes measured median when one or more logs have both fields', () => {
    // Sole sample: 11.05 brutto / 10.7 netto = 96.83% efficiency
    const logs: Log[] = [
      log({ kwhCharged: 11.05, kwhAtVehicle: 10.7 }),
      log({ kwhAtVehicle: 13.83 }), // ignored (no brutto)
    ]
    const result = computeChargingEfficiency(logs, { chargingType: 'AC' })
    expect(result.value).toBeCloseTo(10.7 / 11.05, 3)
    expect(result.source).toBe('measured-median')
    expect(result.sampleSize).toBe(1)
  })

  it('uses all qualifying logs (no cap) and reports the full sample size', () => {
    const logs: Log[] = [
      log({ kwhCharged: 10, kwhAtVehicle: 9.0, loggedAt: '2026-05-01' }), // 0.90
      log({ kwhCharged: 10, kwhAtVehicle: 9.5, loggedAt: '2026-05-02' }), // 0.95
      log({ kwhCharged: 10, kwhAtVehicle: 9.2, loggedAt: '2026-05-03' }), // 0.92
      log({ kwhCharged: 10, kwhAtVehicle: 9.6, loggedAt: '2026-05-04' }), // 0.96
      log({ kwhCharged: 10, kwhAtVehicle: 9.4, loggedAt: '2026-05-05' }), // 0.94
      log({ kwhCharged: 10, kwhAtVehicle: 9.1, loggedAt: '2026-05-06' }), // 0.91
    ]
    const result = computeChargingEfficiency(logs, { chargingType: 'AC' })
    // all 6 sorted: 0.90, 0.91, 0.92, 0.94, 0.95, 0.96 -> even count -> median = (0.92+0.94)/2 = 0.93
    expect(result.value).toBeCloseTo(0.93, 3)
    expect(result.sampleSize).toBe(6)
  })

  it('scopes to chargingProviderId when provided', () => {
    const home = 'home-uuid'
    const ionity = 'ionity-uuid'
    const logs: Log[] = [
      log({ kwhCharged: 10, kwhAtVehicle: 9.0, chargingProviderId: ionity }), // 0.90 - excluded
      log({ kwhCharged: 10, kwhAtVehicle: 9.6, chargingProviderId: home }),   // 0.96 - kept
    ]
    const result = computeChargingEfficiency(logs, { chargingType: 'AC', chargingProviderId: home })
    expect(result.value).toBeCloseTo(0.96, 3)
    expect(result.sampleSize).toBe(1)
  })

  it('filters by chargingType (AC vs DC measured separately)', () => {
    const logs: Log[] = [
      log({ kwhCharged: 10, kwhAtVehicle: 9.0, chargingType: 'AC' }),  // 0.90 AC
      log({ kwhCharged: 10, kwhAtVehicle: 9.7, chargingType: 'DC' }),  // 0.97 DC - excluded for AC query
    ]
    const result = computeChargingEfficiency(logs, { chargingType: 'AC' })
    expect(result.value).toBeCloseTo(0.9, 3)
    expect(result.sampleSize).toBe(1)
  })

  it('ignores logs with kwhCharged <= 0 (defensive)', () => {
    const logs: Log[] = [
      log({ kwhCharged: 0, kwhAtVehicle: 0 }),
      log({ kwhCharged: -1, kwhAtVehicle: 1 }),
    ]
    const result = computeChargingEfficiency(logs, { chargingType: 'AC' })
    expect(result.source).toBe('pauschale')
    expect(result.sampleSize).toBe(0)
  })
})

type CostLog = Log & { costEur: number | null }

const costLog = (over: Partial<CostLog>): CostLog => ({
  kwhCharged: null,
  kwhAtVehicle: null,
  chargingType: 'AC',
  chargingProviderId: null,
  loggedAt: '2026-05-01T12:00:00',
  costEur: null,
  ...over,
})

describe('isNettoOnlyCostLog', () => {
  it('is true for AC log with costEur and kwhAtVehicle but no kwhCharged', () => {
    expect(isNettoOnlyCostLog(costLog({ costEur: 4.15, kwhAtVehicle: 13.83 }))).toBe(true)
  })

  it('is true for DC log analogously', () => {
    expect(isNettoOnlyCostLog(costLog({ costEur: 25.00, kwhAtVehicle: 60, chargingType: 'DC' }))).toBe(true)
  })

  it('is false when kwhCharged is set (= real brutto known)', () => {
    expect(isNettoOnlyCostLog(costLog({ costEur: 4.15, kwhAtVehicle: 13.83, kwhCharged: 15.4 }))).toBe(false)
  })

  it('is false when costEur is null (no cost to hint about)', () => {
    expect(isNettoOnlyCostLog(costLog({ kwhAtVehicle: 13.83 }))).toBe(false)
  })

  it('is false for UNKNOWN chargingType (cannot pick AC vs DC efficiency)', () => {
    expect(isNettoOnlyCostLog(costLog({ costEur: 4.15, kwhAtVehicle: 13.83, chargingType: 'UNKNOWN' }))).toBe(false)
  })

  it('is false when log is null/undefined', () => {
    expect(isNettoOnlyCostLog(null)).toBe(false)
    expect(isNettoOnlyCostLog(undefined)).toBe(false)
  })
})

describe('computeRealCostHint', () => {
  it('returns null when log does not qualify', () => {
    const target = costLog({ costEur: 4.15, kwhAtVehicle: 13.83, kwhCharged: 15.4 })
    expect(computeRealCostHint(target, [])).toBeNull()
  })

  it('uses AC pauschale when no measured samples exist', () => {
    const target = costLog({ costEur: 4.15, kwhAtVehicle: 13.83, chargingType: 'AC' })
    const hint = computeRealCostHint(target, [])
    expect(hint).not.toBeNull()
    // 13.83 / 0.9 = 15.367 brutto kWh, cost scaled by same factor: 4.15 * 15.367/13.83 = 4.611
    expect(hint!.bruttoKwh).toBeCloseTo(15.367, 2)
    expect(hint!.bruttoCostEur).toBeCloseTo(4.611, 2)
    // Effective per-netto-kWh rate inkl. AC-loss: 4.611 / 13.83 = 0.333 EUR/kWh
    expect(hint!.bruttoRatePerNettoKwhEur).toBeCloseTo(0.333, 3)
    expect(hint!.source).toBe('pauschale')
    expect(hint!.sampleSize).toBe(0)
    expect(hint!.efficiencyPercent).toBeCloseTo(90.0, 1)
  })

  it('uses measured median when same-tariff samples exist', () => {
    const home = 'home-uuid'
    const target = costLog({ costEur: 4.15, kwhAtVehicle: 13.83, chargingProviderId: home })
    const samples = [
      // measured 96% efficiency at home tariff
      costLog({ kwhCharged: 11.05, kwhAtVehicle: 10.7, chargingProviderId: home, loggedAt: '2026-05-02' }),
    ]
    const hint = computeRealCostHint(target, samples)
    expect(hint).not.toBeNull()
    // 13.83 / (10.7/11.05) = 14.286 brutto, cost 4.15 * 14.286/13.83 = 4.286
    expect(hint!.bruttoCostEur).toBeCloseTo(4.286, 2)
    expect(hint!.source).toBe('measured-median')
    expect(hint!.sampleSize).toBe(1)
    expect(hint!.efficiencyPercent).toBeCloseTo(96.8, 1)
  })

  it('reports full sample count when many measured samples exist', () => {
    const target = costLog({ costEur: 10, kwhAtVehicle: 10, chargingType: 'AC' })
    const samples = [
      costLog({ kwhCharged: 10, kwhAtVehicle: 8.5, loggedAt: '2026-05-01' }),
      costLog({ kwhCharged: 10, kwhAtVehicle: 9.0, loggedAt: '2026-05-02' }),
      costLog({ kwhCharged: 10, kwhAtVehicle: 9.5, loggedAt: '2026-05-03' }),
      costLog({ kwhCharged: 10, kwhAtVehicle: 9.2, loggedAt: '2026-05-04' }),
      costLog({ kwhCharged: 10, kwhAtVehicle: 9.4, loggedAt: '2026-05-05' }),
      costLog({ kwhCharged: 10, kwhAtVehicle: 9.7, loggedAt: '2026-05-06' }),
    ]
    const hint = computeRealCostHint(target, samples)
    // all 6 sorted: 0.85, 0.90, 0.92, 0.94, 0.95, 0.97 → median (0.92+0.94)/2 = 0.93
    expect(hint!.efficiencyPercent).toBeCloseTo(93.0, 1)
    expect(hint!.sampleSize).toBe(6)
  })
})

describe('costBasisKwh', () => {
  it('prefers brutto (kwhCharged) when measured - matches backend costBasisKwh()', () => {
    expect(costBasisKwh({ kwhCharged: 40, kwhAtVehicle: 37.5 })).toBe(40)
  })

  it('falls back to netto (kwhAtVehicle) when no brutto', () => {
    expect(costBasisKwh({ kwhCharged: null, kwhAtVehicle: 37.5 })).toBe(37.5)
  })

  it('returns null when neither field is positive', () => {
    expect(costBasisKwh({ kwhCharged: null, kwhAtVehicle: null })).toBeNull()
    expect(costBasisKwh({ kwhCharged: 0, kwhAtVehicle: 0 })).toBeNull()
  })
})

describe('aggregateGroupCost', () => {
  it('regression: AT_CHARGER group divides by brutto, not netto (62ct, never 66ct)', () => {
    // Backend stored cost = brutto x tariff = 40 x 0.62 = 24.80. The header per-kWh must
    // divide by brutto (40), yielding 0.62 - dividing by netto (37.5) wrongly gave 0.66.
    const subs = [
      costLog({ costEur: 24.8, kwhCharged: 40, kwhAtVehicle: 37.5, chargingType: 'AC' }),
    ]
    const { totalCostEur, costKwh, costIsNettoOnly } = aggregateGroupCost(subs)
    expect(totalCostEur).toBeCloseTo(24.8, 4)
    expect(costKwh).toBeCloseTo(40, 4)
    expect(totalCostEur! / costKwh).toBeCloseTo(0.62, 4)
    expect(costIsNettoOnly).toBe(false)
  })

  it('netto-only group keeps the truthful tariff and flags netto-only', () => {
    // cost stored as netto x tariff; dividing by the same netto reproduces the tariff.
    const subs = [
      costLog({ costEur: 13.83 * 0.62, kwhAtVehicle: 13.83, chargingType: 'AC' }),
      costLog({ costEur: 10.0 * 0.62, kwhAtVehicle: 10.0, chargingType: 'AC' }),
    ]
    const { totalCostEur, costKwh, costIsNettoOnly } = aggregateGroupCost(subs)
    expect(costKwh).toBeCloseTo(23.83, 4)
    expect(totalCostEur! / costKwh).toBeCloseTo(0.62, 4)
    expect(costIsNettoOnly).toBe(true)
  })

  it('mixed group uses per-log basis and flags netto-only when any sub lacks brutto', () => {
    const subs = [
      costLog({ costEur: 20 * 0.62, kwhCharged: 20, kwhAtVehicle: 18.5, chargingType: 'AC' }),
      costLog({ costEur: 10 * 0.62, kwhAtVehicle: 10, chargingType: 'AC' }), // netto-only
    ]
    const { costKwh, costIsNettoOnly } = aggregateGroupCost(subs)
    expect(costKwh).toBeCloseTo(30, 4) // 20 brutto + 10 netto
    expect(costIsNettoOnly).toBe(true)
  })

  it('excludes sub-logs without cost or without any energy basis', () => {
    const subs = [
      costLog({ costEur: 24.8, kwhCharged: 40, kwhAtVehicle: 37.5 }),
      costLog({ costEur: null, kwhCharged: 10 }),          // no cost
      costLog({ costEur: 5, kwhCharged: null, kwhAtVehicle: null }), // cost but no energy
    ]
    const { totalCostEur, costKwh } = aggregateGroupCost(subs)
    expect(totalCostEur).toBeCloseTo(24.8, 4)
    expect(costKwh).toBeCloseTo(40, 4)
  })

  it('returns null cost and zero kWh when no sub carries cost', () => {
    const { totalCostEur, costKwh, costIsNettoOnly } = aggregateGroupCost([
      costLog({ kwhCharged: 10 }),
    ])
    expect(totalCostEur).toBeNull()
    expect(costKwh).toBe(0)
    expect(costIsNettoOnly).toBe(false)
  })
})
