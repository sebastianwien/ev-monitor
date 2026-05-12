export interface PricingInfo {
    monthly: string
    yearly: string
    yearlyMonthly: string
    yearlySavings: string
    currency: string
    // AutoSync Live (Tier 2). Stripe-Produkt existiert aktuell nur in EUR,
    // daher fuer alle Laender EUR-Preise - nur Format/Tausendertrenner
    // pro Locale angepasst.
    liveMonthly: string
    liveYearly: string
}

const PRICING: Record<string, PricingInfo> = {
    DE: { monthly: '3,90 €', yearly: '39 €', yearlyMonthly: '3,25 €', yearlySavings: '2 Monate gratis', currency: 'EUR', liveMonthly: '4,90 €', liveYearly: '49 €' },
    AT: { monthly: '3,90 €', yearly: '39 €', yearlyMonthly: '3,25 €', yearlySavings: '2 Monate gratis', currency: 'EUR', liveMonthly: '4,90 €', liveYearly: '49 €' },
    CH: { monthly: '3,90 €', yearly: '39 €', yearlyMonthly: '3,25 €', yearlySavings: '2 months free', currency: 'EUR', liveMonthly: '4,90 €', liveYearly: '49 €' },
    US: { monthly: '$5.49', yearly: '$54', yearlyMonthly: '$4.50', yearlySavings: '2 months free', currency: 'USD', liveMonthly: '€4.90', liveYearly: '€49' },
    GB: { monthly: '£4.49', yearly: '£44', yearlyMonthly: '£3.67', yearlySavings: '2 months free', currency: 'GBP', liveMonthly: '€4.90', liveYearly: '€49' },
    NO: { monthly: '69 kr', yearly: '690 kr', yearlyMonthly: '57,50 kr', yearlySavings: '2 måneder gratis', currency: 'NOK', liveMonthly: '4,90 €', liveYearly: '49 €' },
    SE: { monthly: '59 kr', yearly: '590 kr', yearlyMonthly: '49,17 kr', yearlySavings: '2 månader gratis', currency: 'SEK', liveMonthly: '4,90 €', liveYearly: '49 €' },
    DK: { monthly: '39 kr', yearly: '390 kr', yearlyMonthly: '32,50 kr', yearlySavings: '2 months free', currency: 'DKK', liveMonthly: '4,90 €', liveYearly: '49 €' },
}

const DEFAULT_PRICING: PricingInfo = {
    monthly: '€4.90', yearly: '€49', yearlyMonthly: '€4.08', yearlySavings: '2 months free', currency: 'EUR',
    liveMonthly: '€4.90', liveYearly: '€49',
}

export function getPricing(country: string): PricingInfo {
    return PRICING[country] ?? DEFAULT_PRICING
}
