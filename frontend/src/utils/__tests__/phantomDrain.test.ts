import { describe, it, expect } from 'vitest'
import { PHANTOM_EUR_PER_KWH, sumPhantomKwh, phantomEur } from '../phantomDrain'

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
})
