import type { CurrencyCode } from './unitSystems'

/**
 * Static EUR exchange rates for approximate cost display.
 * Updated monthly. Community cost averages are inherently approximate
 * (spread: 20-80 ct/kWh), so 3-8% currency fluctuation is acceptable noise.
 */
export const EUR_EXCHANGE_RATES: Record<CurrencyCode, number> = {
    EUR: 1,
    GBP: 0.86,
    NOK: 11.5,
    SEK: 11.2,
    DKK: 7.45,
    USD: 1.08,
}

export const RATES_LAST_UPDATED = '2026-04-07'

/** Convert EUR amount to target currency */
export function convertFromEur(eur: number, currency: CurrencyCode): number {
    return eur * EUR_EXCHANGE_RATES[currency]
}
