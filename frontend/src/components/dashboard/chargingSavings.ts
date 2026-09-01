/**
 * Heimlade-Ersparnis: Typen und der lokale "Was waere wenn"-Preis.
 *
 * Der Override ist reine Ansichtssache pro Geraet und bleibt deshalb im localStorage -
 * er gehoert nicht auf den Server, weil er keine Aussage ueber den Nutzer trifft,
 * sondern eine Frage von ihm ist.
 */

export type PriceBasis = 'OWN_LOGS' | 'HOME_CARD' | 'OWN_PUBLIC' | 'REGION' | 'COUNTRY' | 'NONE'

export interface ChargingSavings {
  homeKwh: number
  homePricePerKwh: number
  homePriceBasis: PriceBasis
  publicPricePerKwh: number
  publicPriceBasis: PriceBasis
  publicPriceSampleSize: number
  actuallyPaidEur: number
  wouldHaveCostEur: number
  savingsEur: number
  investmentEur: number | null
  recoveredEur: number | null
  amortisationYearsRemaining: number | null
  fullyAmortised: boolean
  /** Gesetzt, sobald ein eigener Vergleichspreis eingegeben wurde. */
  isOverridden?: boolean
}

export const OVERRIDE_STORAGE_KEY = 'evmonitor.savings.publicPriceOverride'

/** Dasselbe Fenster wie im Backend - darueber Tippfehler, darunter Unsinn. */
const MIN_PRICE = 0
const MAX_PRICE = 2

/**
 * Rechnet die Kachel auf einen selbst eingegebenen Vergleichspreis um.
 *
 * Der Heimanteil bleibt unberuehrt - ueberschrieben wird nur, was eine Ladung
 * ausserhalb gekostet haette. Die Amortisation wandert mit, sonst stuenden zwei
 * Zahlen im selben Bild, die sich widersprechen.
 */
export function applyPublicPriceOverride(
  savings: ChargingSavings,
  overridePricePerKwh: number | null,
): ChargingSavings {
  if (overridePricePerKwh == null) return savings

  const wouldHaveCostEur = savings.homeKwh * overridePricePerKwh
  const savingsEur = wouldHaveCostEur - savings.actuallyPaidEur

  // Bisherige Nutzungsdauer aus dem Serverergebnis zurueckgerechnet, damit die
  // kumulierte Zahl auf derselben Basis steht wie ohne Override.
  const usageYears = savings.savingsEur !== 0 && savings.recoveredEur != null
    ? savings.recoveredEur / savings.savingsEur
    : 1
  const recoveredEur = savingsEur * usageYears

  let amortisationYearsRemaining: number | null = null
  let fullyAmortised = false
  if (savings.investmentEur != null && savingsEur > 0) {
    const open = savings.investmentEur - recoveredEur
    fullyAmortised = open <= 0
    amortisationYearsRemaining = fullyAmortised ? 0 : open / savingsEur
  }

  return {
    ...savings,
    publicPricePerKwh: overridePricePerKwh,
    wouldHaveCostEur,
    savingsEur,
    recoveredEur,
    amortisationYearsRemaining,
    fullyAmortised,
    isOverridden: true,
  }
}

export function loadPublicPriceOverride(): number | null {
  const raw = localStorage.getItem(OVERRIDE_STORAGE_KEY)
  if (raw == null) return null
  const value = Number(raw)
  return isPlausible(value) ? value : null
}

/** @returns false, wenn der Wert ausserhalb des plausiblen Fensters liegt */
export function savePublicPriceOverride(pricePerKwh: number): boolean {
  if (!isPlausible(pricePerKwh)) return false
  localStorage.setItem(OVERRIDE_STORAGE_KEY, String(pricePerKwh))
  return true
}

export function clearPublicPriceOverride(): void {
  localStorage.removeItem(OVERRIDE_STORAGE_KEY)
}

function isPlausible(value: number): boolean {
  return Number.isFinite(value) && value >= MIN_PRICE && value <= MAX_PRICE
}
