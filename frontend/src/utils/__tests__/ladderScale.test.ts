import { describe, it, expect } from 'vitest'
import { buildLadderAxis, ladderPosition } from '../ladderScale'

describe('buildLadderAxis', () => {
  it('rounds kWh/100km data to nice bounds with step 5', () => {
    const axis = buildLadderAxis([14.2, 17.8, 24.9], 'kWh/100km')
    expect(axis.min).toBe(10)
    expect(axis.max).toBe(25)
    expect(axis.ticks).toEqual([10, 15, 20, 25])
  })

  it('works in mi/kWh with ascending ticks', () => {
    // 15 / 20 / 25 kWh/100km → 4.14 / 3.11 / 2.49 mi/kWh
    const axis = buildLadderAxis([15, 20, 25], 'mi/kWh')
    expect(axis.min).toBe(2)
    expect(axis.max).toBe(4.5)
    expect(axis.ticks).toEqual([2, 2.5, 3, 3.5, 4, 4.5])
  })

  it('works in kWh/mil (one tenth of kWh/100km)', () => {
    const axis = buildLadderAxis([14.2, 17.8, 24.9], 'kWh/mil')
    expect(axis.min).toBe(1)
    expect(axis.max).toBe(2.5)
    expect(axis.ticks).toEqual([1, 1.5, 2, 2.5])
  })

  it('caps outliers at the plausible window instead of stretching the axis', () => {
    const axis = buildLadderAxis([15, 20, 60], 'kWh/100km')
    expect(axis.max).toBe(35)
  })

  it('clips the axis to the 5th to 95th percentile so a few outliers do not squeeze the rest', () => {
    // 20 models between 14 and 20, one at 33: the axis ends at 20, the outlier pins to the edge
    const values = Array.from({ length: 20 }, (_, i) => 14 + (i * 6) / 19)
    const axis = buildLadderAxis([...values, 33], 'kWh/100km')
    expect(axis.max).toBe(20)
    expect(ladderPosition(33, axis)).toBe(100)
  })

  it('ignores null and non-positive values', () => {
    const axis = buildLadderAxis([null, 0, 18, 22, undefined], 'kWh/100km')
    expect(axis.min).toBe(18)
    expect(axis.max).toBe(22)
  })

  it('falls back to the plausible window without data', () => {
    const axis = buildLadderAxis([], 'kWh/100km')
    expect(axis.min).toBe(10)
    expect(axis.max).toBe(35)
    expect(axis.ticks[0]).toBe(10)
  })

  it('widens a single value to one step so the axis has a span', () => {
    const axis = buildLadderAxis([18], 'kWh/100km')
    expect(axis.max).toBeGreaterThan(axis.min)
    expect(ladderPosition(18, axis)).toBeGreaterThan(0)
    expect(ladderPosition(18, axis)).toBeLessThan(100)
  })
})

describe('ladderPosition', () => {
  const axis = buildLadderAxis([14.2, 17.8, 24.9], 'kWh/100km') // 10..25

  it('maps a value linearly to percent', () => {
    expect(ladderPosition(17.5, axis)).toBeCloseTo(50, 5)
    expect(ladderPosition(10, axis)).toBe(0)
    expect(ladderPosition(25, axis)).toBe(100)
  })

  it('clamps values outside the axis to the edges', () => {
    expect(ladderPosition(40, axis)).toBe(100)
    expect(ladderPosition(5, axis)).toBe(0)
  })

  it('positions in the display unit for mi/kWh', () => {
    const mi = buildLadderAxis([15, 20, 25], 'mi/kWh') // 2..4.5
    // 20 kWh/100km = 3.107 mi/kWh → (3.107 - 2) / 2.5
    expect(ladderPosition(20, mi)).toBeCloseTo(44.3, 1)
  })

  it('returns null for missing values', () => {
    expect(ladderPosition(null, axis)).toBeNull()
  })
})
