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
  /** Erstes Jahr mit belegtem Heimladen - Beginn der Amortisationsschiene. */
  firstYear: number | null
  /** Ersparnis je Kalenderjahr, aus den tatsaechlichen Logs gerechnet. */
  yearlySavings: YearlySaving[]
  recoveredEur: number | null
  amortisationYearsRemaining: number | null
  fullyAmortised: boolean
  /** Gesetzt, sobald ein eigener Vergleichspreis eingegeben wurde. */
  isOverridden?: boolean
}

export interface YearlySaving {
  year: number
  homeKwh: number
  paidEur: number
  wouldHaveCostEur: number
  savingsEur: number
  cumulativeEur: number
}

export type PriceOverrideKind = 'home' | 'public'

export const OVERRIDE_KEYS: Record<PriceOverrideKind, string> = {
  home: 'evmonitor.savings.homePriceOverride',
  public: 'evmonitor.savings.publicPriceOverride',
}

export interface PriceOverrides {
  /** Angenommener Heimstrompreis - der Hebel, den der Nutzer selbst in der Hand hat. */
  home: number | null
  /** Angenommener oeffentlicher Vergleichspreis. */
  public: number | null
}

/** Dasselbe Fenster wie im Backend - darueber Tippfehler, darunter Unsinn. */
const MIN_PRICE = 0
const MAX_PRICE = 2

/**
 * Rechnet die Kachel auf selbst angenommene Preise um.
 *
 * Jedes Jahr wird einzeln neu bewertet, damit die kumulierte Summe auf derselben
 * Grundlage steht wie ohne Annahme. Nicht ueberschriebene Groessen behalten dabei ihr
 * eigenes Jahresniveau - eine Hochrechnung waere hier genauso falsch wie serverseitig.
 */
export function applyPriceOverrides(
  savings: ChargingSavings,
  overrides: PriceOverrides,
): ChargingSavings {
  const { home, public: pub } = overrides
  if (home == null && pub == null) return savings

  const homePricePerKwh = home ?? savings.homePricePerKwh
  const publicPricePerKwh = pub ?? savings.publicPricePerKwh

  const actuallyPaidEur = home != null ? savings.homeKwh * home : savings.actuallyPaidEur
  const wouldHaveCostEur = pub != null ? savings.homeKwh * pub : savings.wouldHaveCostEur
  const savingsEur = wouldHaveCostEur - actuallyPaidEur

  const yearlySavings = (savings.yearlySavings ?? []).map(y => {
    const paidEur = home != null ? y.homeKwh * home : y.paidEur
    const wouldY = pub != null ? y.homeKwh * pub : y.wouldHaveCostEur
    return { ...y, paidEur, wouldHaveCostEur: wouldY, savingsEur: wouldY - paidEur, cumulativeEur: 0 }
  })
  let running = 0
  for (const y of yearlySavings) {
    running += y.savingsEur
    y.cumulativeEur = running
  }
  const recoveredEur = running

  let amortisationYearsRemaining: number | null = null
  let fullyAmortised = false
  if (savings.investmentEur != null && savingsEur > 0) {
    const open = savings.investmentEur - recoveredEur
    fullyAmortised = open <= 0
    amortisationYearsRemaining = fullyAmortised ? 0 : open / savingsEur
  }

  return {
    ...savings,
    homePricePerKwh,
    publicPricePerKwh,
    actuallyPaidEur,
    wouldHaveCostEur,
    savingsEur,
    yearlySavings,
    recoveredEur,
    amortisationYearsRemaining,
    fullyAmortised,
    isOverridden: true,
  }
}

export function loadPriceOverrides(): PriceOverrides {
  return { home: read('home'), public: read('public') }
}

/** @returns false, wenn der Wert ausserhalb des plausiblen Fensters liegt */
export function savePriceOverride(kind: PriceOverrideKind, pricePerKwh: number | null): boolean {
  if (pricePerKwh == null) {
    localStorage.removeItem(OVERRIDE_KEYS[kind])
    return true
  }
  if (!isPlausible(pricePerKwh)) return false
  localStorage.setItem(OVERRIDE_KEYS[kind], String(pricePerKwh))
  return true
}

export function clearPriceOverrides(): void {
  localStorage.removeItem(OVERRIDE_KEYS.home)
  localStorage.removeItem(OVERRIDE_KEYS.public)
}

function read(kind: PriceOverrideKind): number | null {
  const raw = localStorage.getItem(OVERRIDE_KEYS[kind])
  if (raw == null) return null
  const value = Number(raw)
  return isPlausible(value) ? value : null
}

function isPlausible(value: number): boolean {
  return Number.isFinite(value) && value >= MIN_PRICE && value <= MAX_PRICE
}
