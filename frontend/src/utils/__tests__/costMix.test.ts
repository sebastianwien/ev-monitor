import { describe, it, expect } from 'vitest'
import {
  COST_DEFAULTS, HOME_SHARE_WHEN_HOME, DAYS_PER_YEAR, COMBUSTION_MARKETS,
  effectiveHomeShare, mixedPricePerKwh, combustionCostPer100km, savingsPer100km, savingsPerYear,
  combustionAllowed, isCostAssumptions, type CostAssumptions,
} from '../costMix'

const base: CostAssumptions = {
  ...COST_DEFAULTS,
  homePricePerKwh: 0.30,
  publicPricePerKwh: 0.60,
  fuelPricePerLiter: 1.80,
}

describe('costMix', () => {
  it('defaults: home share follows the needs check, petrol at 7.0 l/100 km, consumption as main value', () => {
    expect(COST_DEFAULTS).toEqual({
      homeShare: null, homePricePerKwh: 0.30, publicPricePerKwh: 0.55,
      fuel: 'petrol', litersPer100km: 7.0, fuelPricePerLiter: null, mainValue: 'consumption',
    })
    expect(HOME_SHARE_WHEN_HOME).toBe(0.8)
    expect(DAYS_PER_YEAR).toBe(300)
  })

  it('home share: 80 % with home charging, 0 % without, an explicit value wins', () => {
    expect(effectiveHomeShare(null, true)).toBe(0.8)
    expect(effectiveHomeShare(null, false)).toBe(0)
    expect(effectiveHomeShare(0.5, false)).toBe(0.5)
  })

  it('mixed price blends home and public by the home share', () => {
    expect(mixedPricePerKwh(base, 0.8)).toBeCloseTo(0.36, 6)
    expect(mixedPricePerKwh(base, 0)).toBeCloseTo(0.60, 6)
    expect(mixedPricePerKwh(base, 1)).toBeCloseTo(0.30, 6)
  })

  it('combustion cost and savings per 100 km, positive when the EV is cheaper', () => {
    expect(combustionCostPer100km(base)).toBeCloseTo(12.6, 6)
    // EV at 18 kWh × 0.36 = 6.48 → saves 6.12
    expect(savingsPer100km(6.48, base)).toBeCloseTo(6.12, 6)
    // without a fuel price there is no comparison
    expect(savingsPer100km(6.48, { ...base, fuelPricePerLiter: null })).toBeNull()
    expect(combustionCostPer100km({ ...base, fuelPricePerLiter: 0 })).toBeNull()
  })

  it('savings per year use the daily distance over 300 days', () => {
    // 6.12 per 100 km × (40 × 300 / 100) = 734.4
    expect(savingsPerYear(6.12, 40)).toBeCloseTo(734.4, 6)
    expect(savingsPerYear(6.12, 0)).toBe(0)
  })

  it('combustion comparison only in metric markets', () => {
    expect(COMBUSTION_MARKETS).toEqual(['de', 'en', 'no', 'se'])
    expect(combustionAllowed('de')).toBe(true)
    expect(combustionAllowed('gb')).toBe(false)
    expect(combustionAllowed('us')).toBe(false)
  })

  it('validates stored assumptions', () => {
    expect(isCostAssumptions(base)).toBe(true)
    expect(isCostAssumptions({ ...base, homeShare: 1.5 })).toBe(false)
    expect(isCostAssumptions({ ...base, fuel: 'lpg' })).toBe(false)
    expect(isCostAssumptions({ ...base, litersPer100km: -1 })).toBe(false)
    expect(isCostAssumptions({ ...base, homePricePerKwh: 'x' })).toBe(false)
    expect(isCostAssumptions(null)).toBe(false)
  })
})
