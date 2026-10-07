/**
 * Cost assumptions for the model overview: a blended electricity price (home and public by
 * the buyer's home-charging share) and the combustion car to compare against. Pure arithmetic,
 * the consumption itself comes from the API.
 *
 * Assumptions (named constants, shown in the UI under "Annahmen"):
 * - HOME_SHARE_WHEN_HOME: 80 % of the energy is charged at home when the buyer can
 * - DAYS_PER_YEAR: the daily distance counts 300 days a year
 * - COMBUSTION_MARKETS: comparison only where litres and km are the unit
 * - COMBUSTION_LITERS_BY_CLASS: real-world litres of a combustion car in the model's class
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
  /** null = litres of the model's vehicle class (COMBUSTION_LITERS_BY_CLASS), a number applies to every class */
  litersPer100km: number | null
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

export interface ClassLiters { petrol: number; diesel: number }

/**
 * Real-world consumption of a combustion car per vehicle class (backend `VehicleCategory`),
 * l/100 km. Midpoints of published real-world ranges (Spritmonitor community data as
 * quoted by German consumer guides, EU OBFCM: roughly 20 % above WLTP). Sports car, van
 * and pickup are editorial estimates, no class average was published for them.
 */
export const COMBUSTION_LITERS_BY_CLASS: Record<string, ClassLiters> = {
  CITY_CAR: { petrol: 5.8, diesel: 4.6 },
  COMPACT: { petrol: 6.8, diesel: 5.2 },
  SEDAN: { petrol: 7.6, diesel: 6.0 },
  SUV: { petrol: 8.4, diesel: 6.6 },
  LARGE_SUV: { petrol: 10.5, diesel: 8.0 },
  LUXURY: { petrol: 9.8, diesel: 7.4 },
  SPORTS: { petrol: 10.5, diesel: 8.0 },
  VAN: { petrol: 8.5, diesel: 6.8 },
  PICKUP: { petrol: 11.0, diesel: 9.0 },
}
/** Models without a known class compare against a mid-size car */
export const COMBUSTION_LITERS_FALLBACK: ClassLiters = { petrol: 7.5, diesel: 6.0 }

export const COST_DEFAULTS: CostAssumptions = {
  homeShare: null,
  homePricePerKwh: 0.30,
  publicPricePerKwh: 0.55,
  fuel: 'petrol',
  litersPer100km: null,
  fuelPricePerLiter: null,
  mainValue: 'consumption',
}

/** Litres the comparison uses for a model: the typed value, else the class value for the chosen fuel. */
export function combustionLiters(a: Pick<CostAssumptions, 'litersPer100km' | 'fuel'>, category: string | null | undefined): number {
  if (a.litersPer100km != null) return a.litersPer100km
  const byClass = category != null ? COMBUSTION_LITERS_BY_CLASS[category] : undefined
  return (byClass ?? COMBUSTION_LITERS_FALLBACK)[a.fuel]
}

export function effectiveHomeShare(homeShare: number | null, homeCharging: boolean): number {
  if (homeShare != null) return homeShare
  return homeCharging ? HOME_SHARE_WHEN_HOME : 0
}

export function mixedPricePerKwh(a: Pick<CostAssumptions, 'homePricePerKwh' | 'publicPricePerKwh'>, homeShare: number): number {
  return homeShare * a.homePricePerKwh + (1 - homeShare) * a.publicPricePerKwh
}

type CombustionInput = Pick<CostAssumptions, 'litersPer100km' | 'fuel' | 'fuelPricePerLiter'>

export function combustionCostPer100km(a: CombustionInput, category: string | null | undefined): number | null {
  const liters = combustionLiters(a, category)
  if (a.fuelPricePerLiter == null || !(a.fuelPricePerLiter > 0) || !(liters > 0)) return null
  return liters * a.fuelPricePerLiter
}

/** Positive when the EV is cheaper per 100 km. */
export function savingsPer100km(evCostPer100km: number, a: CombustionInput, category: string | null | undefined): number | null {
  const combustion = combustionCostPer100km(a, category)
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
    && (o.litersPer100km === null || num(o.litersPer100km, 0, LITERS_MAX))
    && (o.fuelPricePerLiter === null || num(o.fuelPricePerLiter, 0, FUEL_PRICE_MAX))
    && (o.mainValue === 'consumption' || o.mainValue === 'cost')
}
