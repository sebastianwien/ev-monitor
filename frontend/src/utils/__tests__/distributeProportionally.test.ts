import { describe, it, expect } from 'vitest'
import { distributeProportionally } from '../distributeProportionally'

describe('distributeProportionally', () => {
  it('distributes total proportionally by weights', () => {
    const result = distributeProportionally(66, [10, 20, 30])
    expect(result).toEqual([11, 22, 33])
    expect(result.reduce((s, v) => s + v, 0)).toBe(66)
  })

  it('last entry absorbs rounding remainder', () => {
    // 10 / 3 = 3.33..., ensure sum equals total exactly
    const result = distributeProportionally(10, [1, 1, 1])
    expect(result.reduce((s, v) => Math.round((s + v) * 100) / 100, 0)).toBe(10)
  })

  it('single entry gets full total', () => {
    expect(distributeProportionally(54.73, [1])).toEqual([54.73])
  })

  it('handles unequal weights correctly', () => {
    const result = distributeProportionally(100, [1, 3])
    expect(result[0]).toBe(25)
    expect(result[1]).toBe(75)
    expect(result[0] + result[1]).toBe(100)
  })

  it('rounds individual values to 2 decimal places', () => {
    const result = distributeProportionally(10, [1, 2])
    result.forEach(v => {
      expect(Number(v.toFixed(2))).toBe(v)
    })
  })

  it('returns empty array for empty weights', () => {
    expect(distributeProportionally(100, [])).toEqual([])
  })

  it('returns zeros when weightSum is 0', () => {
    expect(distributeProportionally(100, [0, 0])).toEqual([0, 0])
  })
})
