import { describe, it, expect } from 'vitest'
import {
  PHANTOM_EUR_PER_KWH,
  sumPhantomKwh,
  phantomEur,
  chargeCostPerKwh,
  phantomEurFor,
  precedingChargePricePerKwh,
} from '../phantomDrain'

const e = (kwh: number | null) => ({ _phantomDrain: kwh == null ? null : { kwh } })

describe('phantomDrain', () => {
  describe('sumPhantomKwh', () => {
    it('sums drain across entries, ignoring missing/empty', () => {
      expect(sumPhantomKwh([e(1.2), e(null), e(0.8), {}])).toBeCloseTo(2.0)
    })
    it('handles null and empty input', () => {
      expect(sumPhantomKwh(null)).toBe(0)
      expect(sumPhantomKwh([])).toBe(0)
    })
  })

  describe('phantomEur', () => {
    it('applies the assumed price', () => {
      expect(phantomEur(10)).toBeCloseTo(10 * PHANTOM_EUR_PER_KWH)
    })
  })

  describe('chargeCostPerKwh', () => {
    it('derives price from gross kWh (kwhCharged) when present', () => {
      expect(chargeCostPerKwh({ costEur: 10, kwhCharged: 25, kwhAtVehicle: 20 }))
        .toBeCloseTo(0.4)
    })
    it('falls back to kwhAtVehicle when kwhCharged is missing', () => {
      expect(chargeCostPerKwh({ costEur: 8, kwhAtVehicle: 20 })).toBeCloseTo(0.4)
    })
    it('returns null for trips', () => {
      expect(chargeCostPerKwh({ _isTrip: true, costEur: 10, kwhCharged: 25 })).toBeNull()
    })
    it('returns null when cost or energy is missing/zero', () => {
      expect(chargeCostPerKwh({ kwhCharged: 25 })).toBeNull()
      expect(chargeCostPerKwh({ costEur: 10 })).toBeNull()
      expect(chargeCostPerKwh({ costEur: 10, kwhCharged: 0 })).toBeNull()
    })
    it('derives price from a merged charge group (Ladegruppe)', () => {
      expect(chargeCostPerKwh({ _isLadegruppe: true, _totalCostEur: 12, _totalKwh: 30 }))
        .toBeCloseTo(0.4)
      expect(chargeCostPerKwh({ _isLadegruppe: true, _totalKwh: 30 })).toBeNull()
    })
  })

  describe('phantomEurFor', () => {
    it('uses the resolved preceding-charge price when present', () => {
      expect(phantomEurFor({ kwh: 2, pricePerKwh: 0.5 }, 0.3)).toBeCloseTo(1.0)
    })
    it('falls back to the user average when no preceding-charge price', () => {
      expect(phantomEurFor({ kwh: 2, pricePerKwh: null }, 0.3)).toBeCloseTo(0.6)
      expect(phantomEurFor({ kwh: 2 }, 0.3)).toBeCloseTo(0.6)
    })
    it('returns null when neither a charge price nor a positive average exists', () => {
      expect(phantomEurFor({ kwh: 2, pricePerKwh: null }, null)).toBeNull()
      expect(phantomEurFor({ kwh: 2, pricePerKwh: null }, 0)).toBeNull()
    })
    it('returns null for a missing drain', () => {
      expect(phantomEurFor(null, 0.3)).toBeNull()
      expect(phantomEurFor(undefined, 0.3)).toBeNull()
    })
  })

  describe('precedingChargePricePerKwh', () => {
    // Feed is newest-first; a drain at index i sits between older=i+1 and newer=i.
    const charge = (costEur: number, kwhCharged: number) => ({ costEur, kwhCharged })
    const trip = () => ({ _isTrip: true })

    it('uses the first charge after the drain index (most recent before the gap)', () => {
      // [trip(newer w/ drain), trip(older), charge@0.4, charge@0.2]
      const feed = [trip(), trip(), charge(10, 25), charge(10, 50)]
      expect(precedingChargePricePerKwh(feed, 0)).toBeCloseTo(0.4)
    })
    it('skips trips and charges without cost to the first priced charge', () => {
      const feed = [trip(), { _isTrip: false }, { costEur: null, kwhCharged: 20 }, charge(8, 20)]
      expect(precedingChargePricePerKwh(feed, 0)).toBeCloseTo(0.4)
    })
    it('returns null when no priced charge precedes the drain', () => {
      expect(precedingChargePricePerKwh([trip(), trip()], 0)).toBeNull()
    })
  })
})
