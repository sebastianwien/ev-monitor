/**
 * Cost assumptions for the model overview: a blended electricity price (home and public by
 * the buyer's home-charging share) and the combustion car to compare against. Pure arithmetic,
 * the consumption itself comes from the API.
 *
 * Assumptions (named constants, shown in the UI under "Annahmen"):
 * - HOME_SHARE_WHEN_HOME: 80 % of the energy is charged at home when the buyer can
 * - DAYS_PER_YEAR: the daily distance counts 300 days a year
 * - COMBUSTION_MARKETS: comparison only where litres and km are the unit
 */
import type { Market } from '../composables/useMarketRoute'

export type FuelKind = 'petrol' | 'diesel'
export type MainValue = 'consumption' | 'cost'

export interface CostAssumptions {
  /** null = follow the needs check (home charging yes → HOME_SHARE_WHEN_HOME, no → 0) */
  homeShare: number | null
  homePricePerKwh: number
  publicPricePerKwh: number
  fuel: FuelKind
  litersPer100km: number
  /** null = no comparison (fuel price unknown for this market) */
  fuelPricePerLiter: number | null
  /** what the big number per row shows under "Sparsamste" */
  mainValue: MainValue
}

export const HOME_SHARE_WHEN_HOME = 0.8
export const DAYS_PER_YEAR = 300
export const COMBUSTION_MARKETS: Market[] = ['de', 'en', 'no', 'se']
export const PRICE_MIN = 0.05
export const PRICE_MAX = 1.5
export const LITERS_MAX = 30
export const FUEL_PRICE_MAX = 10

export const COST_DEFAULTS: CostAssumptions = {
  homeShare: null,
  homePricePerKwh: 0.30,
  publicPricePerKwh: 0.55,
  fuel: 'petrol',
  litersPer100km: 7.0,
  fuelPricePerLiter: null,
  mainValue: 'consumption',
}

export function effectiveHomeShare(homeShare: number | null, homeCharging: boolean): number {
  if (homeShare != null) return homeShare
  return homeCharging ? HOME_SHARE_WHEN_HOME : 0
}

export function mixedPricePerKwh(a: Pick<CostAssumptions, 'homePricePerKwh' | 'publicPricePerKwh'>, homeShare: number): number {
  return homeShare * a.homePricePerKwh + (1 - homeShare) * a.publicPricePerKwh
}

export function combustionCostPer100km(a: Pick<CostAssumptions, 'litersPer100km' | 'fuelPricePerLiter'>): number | null {
  if (a.fuelPricePerLiter == null || !(a.fuelPricePerLiter > 0) || !(a.litersPer100km > 0)) return null
  return a.litersPer100km * a.fuelPricePerLiter
}

/** Positive when the EV is cheaper per 100 km. */
export function savingsPer100km(evCostPer100km: number, a: Pick<CostAssumptions, 'litersPer100km' | 'fuelPricePerLiter'>): number | null {
  const combustion = combustionCostPer100km(a)
  return combustion == null ? null : combustion - evCostPer100km
}

export function savingsPerYear(savingsPer100: number, dailyKm: number): number {
  return (savingsPer100 * Math.max(0, dailyKm) * DAYS_PER_YEAR) / 100
}

export function combustionAllowed(market: Market): boolean {
  return COMBUSTION_MARKETS.includes(market)
}

export function isCostAssumptions(v: unknown): v is CostAssumptions {
  if (!v || typeof v !== 'object') return false
  const o = v as Record<string, unknown>
  const num = (x: unknown, min: number, max: number) => typeof x === 'number' && Number.isFinite(x) && x >= min && x <= max
  return (o.homeShare === null || num(o.homeShare, 0, 1))
    && num(o.homePricePerKwh, 0, PRICE_MAX)
    && num(o.publicPricePerKwh, 0, PRICE_MAX)
    && (o.fuel === 'petrol' || o.fuel === 'diesel')
    && num(o.litersPer100km, 0, LITERS_MAX)
    && (o.fuelPricePerLiter === null || num(o.fuelPricePerLiter, 0, FUEL_PRICE_MAX))
    && (o.mainValue === 'consumption' || o.mainValue === 'cost')
}
