import { describe, it, expect } from 'vitest'
import {
  CHARGE_WINDOWS,
  windowFraction,
  calcRangeKm,
  buildConsumptionScale,
  buildMarkers,
  clampConsumption,
  CONSUMPTION_ABS_MIN,
  CONSUMPTION_ABS_MAX,
  MIN_SCALE_SPAN,
} from '../rangeCalculator'

describe('rangeCalculator', () => {
  describe('windowFraction', () => {
    it('turns a SoC window into the fraction of net capacity used', () => {
      expect(windowFraction({ from: 100, to: 10 })).toBeCloseTo(0.9)
      expect(windowFraction({ from: 90, to: 10 })).toBeCloseTo(0.8)
      expect(windowFraction({ from: 80, to: 20 })).toBeCloseTo(0.6)
      expect(windowFraction({ from: 80, to: 10 })).toBeCloseTo(0.7)
    })

    it('returns null for an empty or inverted window', () => {
      expect(windowFraction({ from: 50, to: 50 })).toBeNull()
      expect(windowFraction({ from: 10, to: 80 })).toBeNull()
    })
  })

  describe('CHARGE_WINDOWS', () => {
    it('starts with the 100→10 window so the default matches the hero metric', () => {
      expect(CHARGE_WINDOWS[0]).toEqual({ from: 100, to: 10 })
    })

    it('offers only valid windows', () => {
      for (const w of CHARGE_WINDOWS) expect(windowFraction(w)).not.toBeNull()
    })
  })

  describe('calcRangeKm', () => {
    // 75 kWh netto, 90 Prozentpunkte Fenster = 67,5 kWh / 18 kWh/100km = 375 km
    it('computes range from net capacity, consumption and window', () => {
      expect(calcRangeKm(75, 18, { from: 100, to: 10 })).toBe(380)
    })

    it('reproduces the hero formula for the 100→10 window', () => {
      // Wortlaut aus PublicModelViewV2.vue (displayRange)
      const heroFormula = Math.round((75 * 0.9) / 18 * 10) * 10
      expect(calcRangeKm(75, 18, { from: 100, to: 10 })).toBe(heroFormula)
    })

    it('scales down with a narrower window', () => {
      // 75 * 0.6 / 18 = 250
      expect(calcRangeKm(75, 18, { from: 80, to: 20 })).toBe(250)
    })

    it('rounds to the nearest 10 km like the hero metric', () => {
      // 75 * 0.9 / 17 = 397.06 -> 400
      expect(calcRangeKm(75, 17, { from: 100, to: 10 })).toBe(400)
    })

    it('returns null when an input is missing', () => {
      expect(calcRangeKm(null, 18, { from: 100, to: 10 })).toBeNull()
      expect(calcRangeKm(75, null, { from: 100, to: 10 })).toBeNull()
    })

    it('returns null for non-positive inputs instead of Infinity or negatives', () => {
      expect(calcRangeKm(75, 0, { from: 100, to: 10 })).toBeNull()
      expect(calcRangeKm(0, 18, { from: 100, to: 10 })).toBeNull()
      expect(calcRangeKm(75, -18, { from: 100, to: 10 })).toBeNull()
      expect(calcRangeKm(-75, 18, { from: 100, to: 10 })).toBeNull()
    })

    it('returns null for an invalid window', () => {
      expect(calcRangeKm(75, 18, { from: 10, to: 80 })).toBeNull()
    })
  })

  describe('buildConsumptionScale', () => {
    it('brackets all markers with headroom on both sides', () => {
      const scale = buildConsumptionScale([16.5, 18.2, 22.4])
      expect(scale.min).toBeLessThan(16.5)
      expect(scale.max).toBeGreaterThan(22.4)
    })

    it('leaves more headroom above than below - winter and Autobahn sit above the markers', () => {
      const scale = buildConsumptionScale([16, 20])
      expect(scale.max - 20).toBeGreaterThan(16 - scale.min)
    })

    it('widens a narrow marker cluster to a usable span', () => {
      const scale = buildConsumptionScale([18, 18.2])
      expect(scale.max - scale.min).toBeGreaterThanOrEqual(MIN_SCALE_SPAN)
    })

    it('keeps integer bounds so the scale labels stay readable', () => {
      const scale = buildConsumptionScale([16.4, 23.7])
      expect(Number.isInteger(scale.min)).toBe(true)
      expect(Number.isInteger(scale.max)).toBe(true)
    })

    it('never leaves the absolute bounds, even for extreme markers', () => {
      const scale = buildConsumptionScale([3, 90])
      expect(scale.min).toBeGreaterThanOrEqual(CONSUMPTION_ABS_MIN)
      expect(scale.max).toBeLessThanOrEqual(CONSUMPTION_ABS_MAX)
    })

    it('still returns a usable span when markers are missing', () => {
      const scale = buildConsumptionScale([])
      expect(scale.max - scale.min).toBeGreaterThanOrEqual(MIN_SCALE_SPAN)
      expect(scale.min).toBeGreaterThanOrEqual(CONSUMPTION_ABS_MIN)
    })

    it('ignores null, zero and negative markers', () => {
      expect(buildConsumptionScale([null, 18, 0, -5, undefined])).toEqual(
        buildConsumptionScale([18]),
      )
    })
  })

  describe('buildMarkers', () => {
    it('sorts ascending by consumption so chips read sparse to hungry', () => {
      const markers = buildMarkers([
        { key: 'winter', label: 'Winter', value: 21.8 },
        { key: 'official', label: 'WLTP', value: 16.9 },
        { key: 'summer', label: 'Sommer', value: 16.4 },
      ])
      expect(markers.map(m => m.key)).toEqual(['summer', 'official', 'winter'])
    })

    it('drops markers without a usable value', () => {
      const markers = buildMarkers([
        { key: 'official', label: 'WLTP', value: 16.9 },
        { key: 'average', label: 'Ø', value: null },
        { key: 'summer', label: 'Sommer', value: undefined },
        { key: 'winter', label: 'Winter', value: 0 },
      ])
      expect(markers.map(m => m.key)).toEqual(['official'])
    })

    it('collapses markers that share a value - two chips on one slider position confuse', () => {
      const markers = buildMarkers([
        { key: 'official', label: 'WLTP', value: 18 },
        { key: 'average', label: 'Ø', value: 18 },
      ])
      expect(markers).toEqual([{ key: 'official', label: 'WLTP', value: 18 }])
    })

    it('returns an empty list when nothing is populated', () => {
      expect(buildMarkers([{ key: 'average', label: 'Ø', value: null }])).toEqual([])
    })
  })

  describe('clampConsumption', () => {
    const scale = { min: 14, max: 26 }

    it('keeps a value inside the scale untouched', () => {
      expect(clampConsumption(18.3, scale)).toBe(18.3)
    })

    it('clamps outside values to the scale bounds', () => {
      expect(clampConsumption(9, scale)).toBe(14)
      expect(clampConsumption(40, scale)).toBe(26)
    })
  })
})
