import { describe, it, expect } from 'vitest'
import { rippleInfluence, cursorInfluence } from '../useGridRipple'

describe('rippleInfluence', () => {
  it('ist maximal genau auf dem Ringradius (dist == radius)', () => {
    const onBand = rippleInfluence(100, 100, 46, 1)
    const offBand = rippleInfluence(160, 100, 46, 1)
    expect(onBand).toBeCloseTo(1, 5)
    expect(onBand).toBeGreaterThan(offBand)
  })

  it('klingt mit der Bandbreite gaussisch ab', () => {
    // Abstand == width entspricht exp(-1) ~ 0.368
    const v = rippleInfluence(146, 100, 46, 1)
    expect(v).toBeCloseTo(Math.exp(-1), 3)
  })

  it('liefert 0 bei totem Ripple (life <= 0)', () => {
    expect(rippleInfluence(100, 100, 46, 0)).toBe(0)
    expect(rippleInfluence(100, 100, 46, -0.5)).toBe(0)
  })

  it('daempft quadratisch mit der Restlebensdauer', () => {
    const full = rippleInfluence(100, 100, 46, 1)
    const half = rippleInfluence(100, 100, 46, 0.5)
    // life^2: 0.5^2 = 0.25
    expect(half).toBeCloseTo(full * 0.25, 5)
  })
})

describe('cursorInfluence', () => {
  it('ist 1 direkt am Cursor (dist == 0)', () => {
    expect(cursorInfluence(0, 110)).toBeCloseTo(1, 5)
  })

  it('ist 0 am Radius und darueber', () => {
    expect(cursorInfluence(110, 110)).toBe(0)
    expect(cursorInfluence(200, 110)).toBe(0)
  })

  it('faellt monoton mit der Distanz (smoothstep)', () => {
    const near = cursorInfluence(20, 110)
    const mid = cursorInfluence(55, 110)
    const far = cursorInfluence(90, 110)
    expect(near).toBeGreaterThan(mid)
    expect(mid).toBeGreaterThan(far)
  })

  it('hat in der Mitte den Smoothstep-Wert 0.5', () => {
    expect(cursorInfluence(55, 110)).toBeCloseTo(0.5, 5)
  })
})
