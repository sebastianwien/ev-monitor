import type { ConsumptionUnit, CurrencyCode, UnitSystem } from '../config/unitSystems'
import { EUR_EXCHANGE_RATES } from '../config/exchangeRates'

const KM_PER_MILE = 1.60934
const MILES_PER_100KM = 100 / KM_PER_MILE // 62.1371

/** Convert kWh/100km to the target consumption unit */
export function convertConsumption(kwhPer100km: number, unit: ConsumptionUnit): number {
    if (kwhPer100km <= 0) return 0
    switch (unit) {
        case 'kWh/mil':
            return kwhPer100km / 10
        case 'mi/kWh':
            return MILES_PER_100KM / kwhPer100km
        default:
            return kwhPer100km
    }
}

/** Convert km to miles */
export function convertDistance(km: number, toMiles: boolean): number {
    return toMiles ? km / KM_PER_MILE : km
}

/** Convert EUR to target currency */
export function convertCurrency(eur: number, currency: CurrencyCode): number {
    return eur * EUR_EXCHANGE_RATES[currency]
}

/** Convert EUR/100km to local-currency/100-distance-unit */
export function convertCostPerDistance(eurPer100km: number, system: UnitSystem): number {
    const inLocalCurrency = eurPer100km * EUR_EXCHANGE_RATES[system.currency]
    // For imperial: 100km cost -> 100mi cost (100mi = 160.9km, so multiply by 1.609)
    return system.distanceUnit === 'miles' ? inLocalCurrency * KM_PER_MILE : inLocalCurrency
}

/**
 * Compute WLTP delta percentage.
 * Always in kWh/100km space: positive = worse than WLTP.
 */
export function consumptionDeltaPercent(realKwhPer100km: number, wltpKwhPer100km: number): number {
    if (wltpKwhPer100km <= 0) return 0
    return ((realKwhPer100km - wltpKwhPer100km) / wltpKwhPer100km) * 100
}
