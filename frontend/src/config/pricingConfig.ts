export interface PricingInfo {
    monthly: string
    yearly: string
    yearlyMonthly: string
    yearlySavings: string
    currency: string
}

const PRICING: Record<string, PricingInfo> = {
    DE: { monthly: '3,90 €', yearly: '39 €', yearlyMonthly: '3,25 €', yearlySavings: '2 Monate gratis', currency: 'EUR' },
    AT: { monthly: '3,90 €', yearly: '39 €', yearlyMonthly: '3,25 €', yearlySavings: '2 Monate gratis', currency: 'EUR' },
    CH: { monthly: '3,90 €', yearly: '39 €', yearlyMonthly: '3,25 €', yearlySavings: '2 months free', currency: 'EUR' },
    US: { monthly: '$5.49', yearly: '$54', yearlyMonthly: '$4.50', yearlySavings: '2 months free', currency: 'USD' },
    GB: { monthly: '£4.49', yearly: '£44', yearlyMonthly: '£3.67', yearlySavings: '2 months free', currency: 'GBP' },
    NO: { monthly: '69 kr', yearly: '690 kr', yearlyMonthly: '57,50 kr', yearlySavings: '2 måneder gratis', currency: 'NOK' },
    SE: { monthly: '59 kr', yearly: '590 kr', yearlyMonthly: '49,17 kr', yearlySavings: '2 månader gratis', currency: 'SEK' },
    DK: { monthly: '39 kr', yearly: '390 kr', yearlyMonthly: '32,50 kr', yearlySavings: '2 months free', currency: 'DKK' },
}

const DEFAULT_PRICING: PricingInfo = {
    monthly: '€4.90', yearly: '€49', yearlyMonthly: '€4.08', yearlySavings: '2 months free', currency: 'EUR',
}

export function getPricing(country: string): PricingInfo {
    return PRICING[country] ?? DEFAULT_PRICING
}
