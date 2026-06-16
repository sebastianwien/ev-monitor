import { describe, it, expect } from 'vitest'
import { PHANTOM_EUR_PER_KWH, sumPhantomKwh, phantomEur, phantomTeaser } from '../phantomDrain'

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

  describe('phantomTeaser', () => {
    it('returns null when there is no drain above the noise threshold', () => {
      expect(phantomTeaser([])).toBeNull()
      expect(phantomTeaser(null)).toBeNull()
      expect(phantomTeaser([e(0.04), e(null)])).toBeNull()
    })

    it('takes the first entry as the latest sample (feed is time-descending)', () => {
      const t = phantomTeaser([e(1.5), e(2.0), e(0.5)])
      expect(t).not.toBeNull()
      expect(t!.latestKwh).toBe(1.5)
      expect(t!.count).toBe(3)
      expect(t!.totalEur).toBeCloseTo(4.0 * PHANTOM_EUR_PER_KWH)
    })

    it('excludes sub-threshold drains from count and total', () => {
      const t = phantomTeaser([e(1.0), e(0.04), e(0.5)])
      expect(t!.count).toBe(2)
      expect(t!.totalEur).toBeCloseTo(1.5 * PHANTOM_EUR_PER_KWH)
    })
  })
})
