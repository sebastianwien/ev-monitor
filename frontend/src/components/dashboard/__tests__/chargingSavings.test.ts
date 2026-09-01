import { describe, it, expect, beforeEach } from 'vitest'
import {
  applyPublicPriceOverride,
  loadPublicPriceOverride,
  savePublicPriceOverride,
  clearPublicPriceOverride,
  OVERRIDE_STORAGE_KEY,
  type ChargingSavings,
} from '../chargingSavings'

/** Der auf Prod gemessene Median-Heimlader. */
const base: ChargingSavings = {
  homeKwh: 640,
  homePricePerKwh: 0.27,
  homePriceBasis: 'OWN_LOGS',
  publicPricePerKwh: 0.40,
  publicPriceBasis: 'COUNTRY',
  publicPriceSampleSize: 2659,
  actuallyPaidEur: 172.8,
  wouldHaveCostEur: 256,
  savingsEur: 83.2,
  investmentEur: 1400,
  recoveredEur: 212.16,
  amortisationYearsRemaining: 14.3,
  fullyAmortised: false,
}

describe('applyPublicPriceOverride', () => {
  it('laesst die Serverzahlen unangetastet, wenn nichts ueberschrieben ist', () => {
    const result = applyPublicPriceOverride(base, null)

    expect(result).toEqual(base)
    expect(result.isOverridden).toBeFalsy()
  })

  it('rechnet die gesamte Kachel auf den eingegebenen Preis um', () => {
    const result = applyPublicPriceOverride(base, 0.55)

    expect(result.publicPricePerKwh).toBe(0.55)
    expect(result.wouldHaveCostEur).toBeCloseTo(352, 2)
    expect(result.savingsEur).toBeCloseTo(179.2, 2)
    expect(result.isOverridden).toBe(true)
  })

  /**
   * Die Amortisation muss mitwandern - zwei Zahlen im selben Bild, die sich
   * widersprechen, zerstoeren das Vertrauen in beide.
   */
  it('zieht die Amortisation mit', () => {
    const result = applyPublicPriceOverride(base, 0.55)

    // 179,20 EUR/Jahr bei 2,55 Jahren Nutzung -> rund 457 EUR eingespielt
    expect(result.recoveredEur).toBeGreaterThan(base.recoveredEur!)
    expect(result.amortisationYearsRemaining!).toBeLessThan(base.amortisationYearsRemaining!)
  })

  it('der Heimanteil bleibt unberuehrt', () => {
    const result = applyPublicPriceOverride(base, 0.55)

    expect(result.actuallyPaidEur).toBe(base.actuallyPaidEur)
    expect(result.homePricePerKwh).toBe(base.homePricePerKwh)
  })

  it('meldet vollstaendige Amortisation, wenn der Override sie erreicht', () => {
    const result = applyPublicPriceOverride({ ...base, investmentEur: 300 }, 0.55)

    expect(result.fullyAmortised).toBe(true)
    expect(result.amortisationYearsRemaining).toBe(0)
  })

  it('ohne Investition bleibt die Amortisation leer', () => {
    const result = applyPublicPriceOverride(
      { ...base, investmentEur: null, recoveredEur: 212.16, amortisationYearsRemaining: null }, 0.55)

    expect(result.amortisationYearsRemaining).toBeNull()
    expect(result.fullyAmortised).toBe(false)
  })

  it('ein Preis unter dem Heimpreis ergibt eine negative Ersparnis', () => {
    const result = applyPublicPriceOverride(base, 0.20)

    expect(result.savingsEur).toBeLessThan(0)
  })
})

describe('Override-Speicher', () => {
  beforeEach(() => localStorage.clear())

  it('speichert und liest den Wert', () => {
    savePublicPriceOverride(0.55)

    expect(loadPublicPriceOverride()).toBe(0.55)
    expect(localStorage.getItem(OVERRIDE_STORAGE_KEY)).toBe('0.55')
  })

  it('loescht ihn wieder', () => {
    savePublicPriceOverride(0.55)
    clearPublicPriceOverride()

    expect(loadPublicPriceOverride()).toBeNull()
  })

  it('ignoriert unbrauchbare Werte im Speicher', () => {
    localStorage.setItem(OVERRIDE_STORAGE_KEY, 'nonsense')

    expect(loadPublicPriceOverride()).toBeNull()
  })

  /** Ein absurder Preis waere keine Spielerei mehr, sondern eine kaputte Kachel. */
  it('weist Werte ausserhalb des plausiblen Fensters ab', () => {
    expect(savePublicPriceOverride(9.99)).toBe(false)
    expect(savePublicPriceOverride(-1)).toBe(false)
    expect(loadPublicPriceOverride()).toBeNull()
  })
})
