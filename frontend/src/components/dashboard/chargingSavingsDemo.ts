import type { ChargingSavings } from './chargingSavings'

/**
 * Schaufenster-Daten fuer die Ersparnis-Kachel im Demo-Modus. Bewusst die echte
 * Komponente statt eines Screenshots: sie bleibt automatisch aktuell, stimmt in jeder
 * Sprache und im Dark Mode. Wird auf /supporter und im XPeng-Upgrade-Teaser genutzt.
 */
export const chargingSavingsDemo: ChargingSavings = {
  homeKwh: 839,
  homePricePerKwh: 0.289,
  homePriceBasis: 'OWN_LOGS',
  publicPricePerKwh: 0.417,
  publicPriceBasis: 'OWN_PUBLIC',
  publicPriceSampleSize: 18,
  actuallyPaidEur: 242.34,
  wouldHaveCostEur: 349.55,
  savingsEur: 107.21,
  investmentEur: 1000,
  firstYear: 2025,
  monthsOfUsage: 14,
  yearlySavings: [
    { year: 2025, homeKwh: 210, paidEur: 60.9, wouldHaveCostEur: 84.5, savingsEur: 23.6, cumulativeEur: 23.6 },
    { year: 2026, homeKwh: 839, paidEur: 242.34, wouldHaveCostEur: 349.55, savingsEur: 60.95, cumulativeEur: 84.55 },
  ],
  recoveredEur: 84.55,
  amortisationYearsRemaining: 8.5,
  fullyAmortised: false,
}
