import { describe, it, expect } from 'vitest'
import {
  COST_DEFAULTS, HOME_SHARE_WHEN_HOME, DAYS_PER_YEAR, COMBUSTION_MARKETS, COMBUSTION_LITERS_BY_CLASS, COMBUSTION_LITERS_FALLBACK,
  effectiveHomeShare, mixedPricePerKwh, combustionLiters, combustionCostPer100km, savingsPer100km, savingsPerYear,
  combustionAllowed, isCostAssumptions, type CostAssumptions,
} from '../costMix'

const base: CostAssumptions = {
  ...COST_DEFAULTS,
  homePricePerKwh: 0.30,
  publicPricePerKwh: 0.60,
  fuelPricePerLiter: 1.80,
}

describe('costMix', () => {
  it('defaults: home share follows the needs check, litres follow the vehicle class, consumption as main value', () => {
    expect(COST_DEFAULTS).toEqual({
      homeShare: null, homePricePerKwh: 0.30, publicPricePerKwh: 0.55,
      fuel: 'petrol', litersPer100km: null, fuelPricePerLiter: null, mainValue: 'consumption',
    })
    expect(HOME_SHARE_WHEN_HOME).toBe(0.8)
    expect(DAYS_PER_YEAR).toBe(300)
  })

  it('litres per class: every backend class has petrol and diesel, diesel below petrol', () => {
    const classes = ['CITY_CAR', 'COMPACT', 'SEDAN', 'SUV', 'LARGE_SUV', 'LUXURY', 'SPORTS', 'VAN', 'PICKUP']
    expect(Object.keys(COMBUSTION_LITERS_BY_CLASS).sort()).toEqual([...classes].sort())
    for (const c of classes) {
      const l = COMBUSTION_LITERS_BY_CLASS[c]
      expect(l.diesel).toBeLessThan(l.petrol)
      expect(l.diesel).toBeGreaterThan(3)
      expect(l.petrol).toBeLessThan(15)
    }
    expect(COMBUSTION_LITERS_BY_CLASS.COMPACT).toEqual({ petrol: 6.8, diesel: 5.2 })
    expect(COMBUSTION_LITERS_BY_CLASS.SUV.petrol).toBeGreaterThan(COMBUSTION_LITERS_BY_CLASS.COMPACT.petrol)
  })

  it('combustionLiters: class value by fuel, fallback for an unknown class, a typed value wins for every class', () => {
    expect(combustionLiters({ litersPer100km: null, fuel: 'petrol' }, 'COMPACT')).toBe(6.8)
    expect(combustionLiters({ litersPer100km: null, fuel: 'diesel' }, 'COMPACT')).toBe(5.2)
    expect(combustionLiters({ litersPer100km: null, fuel: 'petrol' }, 'HOVERCRAFT')).toBe(COMBUSTION_LITERS_FALLBACK.petrol)
    expect(combustionLiters({ litersPer100km: null, fuel: 'diesel' }, null)).toBe(COMBUSTION_LITERS_FALLBACK.diesel)
    expect(combustionLiters({ litersPer100km: 9, fuel: 'petrol' }, 'COMPACT')).toBe(9)
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

  it('combustion cost and savings per 100 km follow the class, positive when the EV is cheaper', () => {
    // compact petrol 6.8 l × 1.80 = 12.24
    expect(combustionCostPer100km(base, 'COMPACT')).toBeCloseTo(12.24, 6)
    // SUV petrol 8.4 l × 1.80 = 15.12
    expect(combustionCostPer100km(base, 'SUV')).toBeCloseTo(15.12, 6)
    // typed 7.0 l for every class
    expect(combustionCostPer100km({ ...base, litersPer100km: 7 }, 'SUV')).toBeCloseTo(12.6, 6)
    // EV at 18 kWh × 0.36 = 6.48 → saves 5.76 against the compact
    expect(savingsPer100km(6.48, base, 'COMPACT')).toBeCloseTo(5.76, 6)
    // without a fuel price there is no comparison
    expect(savingsPer100km(6.48, { ...base, fuelPricePerLiter: null }, 'COMPACT')).toBeNull()
    expect(combustionCostPer100km({ ...base, fuelPricePerLiter: 0 }, 'COMPACT')).toBeNull()
    expect(combustionCostPer100km({ ...base, litersPer100km: 0 }, 'COMPACT')).toBeNull()
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
    expect(isCostAssumptions({ ...base, litersPer100km: null })).toBe(true)
    expect(isCostAssumptions({ ...base, homePricePerKwh: 'x' })).toBe(false)
    expect(isCostAssumptions(null)).toBe(false)
  })
})
