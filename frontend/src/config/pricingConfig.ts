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
  // Supporter (analytics-only upsell). Multi-currency like AutoSync; EUR base is the
  // fallback for countries without a dedicated currency.
  supporterMonthly: string
  supporterYearly: string
}

const PRICING: Record<string, PricingInfo> = {
  DE: { monthly: '3,90 €', yearly: '39 €', yearlyMonthly: '3,25 €', yearlySavings: '2 Monate gratis', currency: 'EUR', liveMonthly: '4,90 €', liveYearly: '49 €', supporterMonthly: '1,99 €', supporterYearly: '19 €' },
  AT: { monthly: '3,90 €', yearly: '39 €', yearlyMonthly: '3,25 €', yearlySavings: '2 Monate gratis', currency: 'EUR', liveMonthly: '4,90 €', liveYearly: '49 €', supporterMonthly: '1,99 €', supporterYearly: '19 €' },
  CH: { monthly: '3,90 €', yearly: '39 €', yearlyMonthly: '3,25 €', yearlySavings: '2 months free', currency: 'EUR', liveMonthly: '4,90 €', liveYearly: '49 €', supporterMonthly: '1,99 €', supporterYearly: '19 €' },
  US: { monthly: '$5.49', yearly: '$54', yearlyMonthly: '$4.50', yearlySavings: '2 months free', currency: 'USD', liveMonthly: '€4.90', liveYearly: '€49', supporterMonthly: '$2.49', supporterYearly: '$24' },
  GB: { monthly: '£4.49', yearly: '£44', yearlyMonthly: '£3.67', yearlySavings: '2 months free', currency: 'GBP', liveMonthly: '€4.90', liveYearly: '€49', supporterMonthly: '£1.99', supporterYearly: '£19' },
  NO: { monthly: '69 kr', yearly: '690 kr', yearlyMonthly: '57,50 kr', yearlySavings: '2 måneder gratis', currency: 'NOK', liveMonthly: '4,90 €', liveYearly: '49 €', supporterMonthly: '29 kr', supporterYearly: '290 kr' },
  SE: { monthly: '59 kr', yearly: '590 kr', yearlyMonthly: '49,17 kr', yearlySavings: '2 månader gratis', currency: 'SEK', liveMonthly: '4,90 €', liveYearly: '49 €', supporterMonthly: '29 kr', supporterYearly: '290 kr' },
  DK: { monthly: '39 kr', yearly: '390 kr', yearlyMonthly: '32,50 kr', yearlySavings: '2 months free', currency: 'DKK', liveMonthly: '4,90 €', liveYearly: '49 €', supporterMonthly: '19 kr', supporterYearly: '190 kr' },
}

const DEFAULT_PRICING: PricingInfo = {
  monthly: '€4.90', yearly: '€49', yearlyMonthly: '€4.08', yearlySavings: '2 months free', currency: 'EUR',
  liveMonthly: '€4.90', liveYearly: '€49',
  supporterMonthly: '€1.99', supporterYearly: '€19',
}

export function getPricing(country: string): PricingInfo {
  return PRICING[country] ?? DEFAULT_PRICING
}
