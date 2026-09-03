import { describe, it, expect, beforeEach } from 'vitest'
import {
  applyPriceOverrides,
  loadPriceOverrides,
  savePriceOverride,
  clearPriceOverrides,
  OVERRIDE_KEYS,
  type ChargingSavings,
} from '../chargingSavings'

/**
 * Drei Jahre Heimladen. Die Summen sind die Summen der Jahresreihe - seit die Kachel
 * ueber die gesamte Laufzeit rechnet, gibt es kein getrenntes 12-Monats-Fenster mehr.
 */
const base: ChargingSavings = {
  homeKwh: 1340,
  homePricePerKwh: 0.27,
  homePriceBasis: 'OWN_LOGS',
  publicPricePerKwh: 0.40,
  publicPriceBasis: 'COUNTRY',
  publicPriceSampleSize: 2659,
  actuallyPaidEur: 361.8,
  wouldHaveCostEur: 536,
  savingsEur: 174.2,
  investmentEur: 1400,
  firstYear: 2024,
  monthsOfUsage: 24,
  yearlySavings: [
    { year: 2024, homeKwh: 200, paidEur: 54, wouldHaveCostEur: 80, savingsEur: 26, cumulativeEur: 26 },
    { year: 2025, homeKwh: 500, paidEur: 135, wouldHaveCostEur: 200, savingsEur: 65, cumulativeEur: 91 },
    { year: 2026, homeKwh: 640, paidEur: 172.8, wouldHaveCostEur: 256, savingsEur: 83.2, cumulativeEur: 174.2 },
  ],
  recoveredEur: 174.2,
  amortisationYearsRemaining: 14.7,
  fullyAmortised: false,
}

const none = { home: null, public: null }

describe('applyPriceOverrides', () => {
  it('laesst die Serverzahlen unangetastet, wenn nichts ueberschrieben ist', () => {
    const result = applyPriceOverrides(base, none)

    expect(result).toEqual(base)
    expect(result.isOverridden).toBeFalsy()
  })

  it('rechnet auf einen eigenen Vergleichspreis um', () => {
    const result = applyPriceOverrides(base, { home: null, public: 0.55 })

    expect(result.publicPricePerKwh).toBe(0.55)
    expect(result.wouldHaveCostEur).toBeCloseTo(737, 2)   // 1340 * 0,55
    expect(result.savingsEur).toBeCloseTo(375.2, 2)
    expect(result.actuallyPaidEur).toBe(base.actuallyPaidEur)
    expect(result.isOverridden).toBe(true)
  })

  /** Der Heimpreis ist der staerkere Hebel: er wirkt auf das, was man selbst aendern kann. */
  it('rechnet auf einen eigenen Heimpreis um', () => {
    const result = applyPriceOverrides(base, { home: 0.18, public: null })

    expect(result.homePricePerKwh).toBe(0.18)
    expect(result.actuallyPaidEur).toBeCloseTo(241.2, 2)  // 1340 * 0,18
    expect(result.wouldHaveCostEur).toBe(base.wouldHaveCostEur)
    expect(result.savingsEur).toBeCloseTo(294.8, 2)
  })

  it('vertraegt beide gleichzeitig', () => {
    const result = applyPriceOverrides(base, { home: 0.18, public: 0.55 })

    expect(result.actuallyPaidEur).toBeCloseTo(241.2, 2)
    expect(result.wouldHaveCostEur).toBeCloseTo(737, 2)
    expect(result.savingsEur).toBeCloseTo(495.8, 2)
  })

  /**
   * Die kumulierte Summe wird Jahr fuer Jahr neu bewertet, nicht hochgerechnet - nicht
   * ueberschriebene Groessen behalten dabei ihr eigenes Jahresniveau.
   */
  it('bewertet jedes Jahr mit dem angenommenen Preis neu', () => {
    const result = applyPriceOverrides(base, { home: 0.10, public: null })

    // 2024: 80 - 20 = 60 | 2025: 200 - 50 = 150 | 2026: 256 - 64 = 192
    expect(result.recoveredEur).toBeCloseTo(402, 2)
    expect(result.yearlySavings[2].savingsEur).toBeCloseTo(192, 2)
  })

  it('zieht die Amortisation mit', () => {
    const result = applyPriceOverrides(base, { home: 0.10, public: null })

    expect(result.recoveredEur!).toBeGreaterThan(base.recoveredEur!)
    expect(result.amortisationYearsRemaining!).toBeLessThan(base.amortisationYearsRemaining!)
  })

  it('meldet vollstaendige Amortisation, wenn die Annahme sie erreicht', () => {
    const result = applyPriceOverrides({ ...base, investmentEur: 300 }, { home: 0.10, public: null })

    expect(result.fullyAmortised).toBe(true)
    expect(result.amortisationYearsRemaining).toBe(0)
  })

  it('ein Vergleichspreis unter dem Heimpreis ergibt eine negative Ersparnis', () => {
    expect(applyPriceOverrides(base, { home: null, public: 0.20 }).savingsEur).toBeLessThan(0)
  })
})

describe('Override-Speicher', () => {
  beforeEach(() => localStorage.clear())

  it('speichert und liest beide getrennt', () => {
    savePriceOverride('home', 0.18)
    savePriceOverride('public', 0.55)

    expect(loadPriceOverrides()).toEqual({ home: 0.18, public: 0.55 })
    expect(localStorage.getItem(OVERRIDE_KEYS.home)).toBe('0.18')
  })

  it('loescht beide', () => {
    savePriceOverride('home', 0.18)
    savePriceOverride('public', 0.55)
    clearPriceOverrides()

    expect(loadPriceOverrides()).toEqual({ home: null, public: null })
  })

  it('null entfernt einen einzelnen Wert', () => {
    savePriceOverride('home', 0.18)
    savePriceOverride('home', null)

    expect(loadPriceOverrides().home).toBeNull()
  })

  it('ignoriert unbrauchbare Werte im Speicher', () => {
    localStorage.setItem(OVERRIDE_KEYS.home, 'nonsense')

    expect(loadPriceOverrides().home).toBeNull()
  })

  /** Ein absurder Preis waere keine Spielerei mehr, sondern eine kaputte Kachel. */
  it('weist Werte ausserhalb des plausiblen Fensters ab', () => {
    expect(savePriceOverride('public', 9.99)).toBe(false)
    expect(savePriceOverride('home', -1)).toBe(false)
    expect(loadPriceOverrides()).toEqual({ home: null, public: null })
  })
})
