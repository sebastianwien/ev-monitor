import { describe, it, expect } from 'vitest'
import { blendedPricePerKwh } from '../publicModelService'

describe('blendedPricePerKwh', () => {
  const home = 0.26
  const pub = 0.55

  it('is the public price at 0% home share', () => {
    expect(blendedPricePerKwh(home, pub, 0)).toBeCloseTo(0.55, 5)
  })

  it('is the home price at 100% home share', () => {
    expect(blendedPricePerKwh(home, pub, 1)).toBeCloseTo(0.26, 5)
  })

  it('blends linearly in between', () => {
    // 70% home: 0.26*0.7 + 0.55*0.3 = 0.182 + 0.165 = 0.347
    expect(blendedPricePerKwh(home, pub, 0.7)).toBeCloseTo(0.347, 5)
  })

  it('clamps shares outside 0..1', () => {
    expect(blendedPricePerKwh(home, pub, -0.5)).toBeCloseTo(pub, 5)
    expect(blendedPricePerKwh(home, pub, 2)).toBeCloseTo(home, 5)
  })
})
